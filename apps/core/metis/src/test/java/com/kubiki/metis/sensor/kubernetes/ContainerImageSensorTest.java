package com.kubiki.metis.sensor.kubernetes;

import com.kubiki.metis.config.MetisProperties;
import com.kubiki.metis.grpc.SensorEvent;
import com.kubiki.metis.sensor.IriFactory;
import com.kubiki.metis.sensor.SensorEventPublisher;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ContainerImageSensorTest {

    private static final String CNEE = "http://www.semanticweb.org/szymo/ontologies/2026/2/CNEEOnt/";

    private SensorEventPublisher publisher;
    private ContainerImageSensor sensor;

    @BeforeEach
    void setUp() {
        MetisProperties props = new MetisProperties(
                new MetisProperties.GraphDB("http://x", "test", 1000),
                new MetisProperties.Ontology(CNEE),
                new MetisProperties.Sensor(true, List.of("sock-shop"), 50, 500));

        IriFactory iriFactory = new IriFactory(props);
        publisher = mock(SensorEventPublisher.class);
        sensor = new ContainerImageSensor(mock(KubernetesClient.class), props, publisher, iriFactory);
    }

    @Test
    void processPod_emitsContainerImageAndRelationships() {
        Pod pod = new PodBuilder()
                .withNewMetadata().withName("front-end-abc").withNamespace("sock-shop").endMetadata()
                .withNewSpec()
                .addToContainers(new ContainerBuilder()
                        .withName("front-end")
                        .withImage("weaveworksdemos/front-end:0.3.0")
                        .build())
                .endSpec()
                .build();

        sensor.processPod(pod);

        ArgumentCaptor<SensorEvent> captor = ArgumentCaptor.forClass(SensorEvent.class);
        verify(publisher, org.mockito.Mockito.atLeast(4)).publish(captor.capture());

        List<SensorEvent> events = captor.getAllValues();
        assertThat(events).anyMatch(e -> e.getEventCase() == SensorEvent.EventCase.ENTITY_DISCOVERED
                && e.getEntityDiscovered().getOntologyType().equals(CNEE + "Container"));
        assertThat(events).anyMatch(e -> e.getEventCase() == SensorEvent.EventCase.ENTITY_DISCOVERED
                && e.getEntityDiscovered().getOntologyType().equals(CNEE + "Image")
                && "0.3.0".equals(e.getEntityDiscovered().getPropertiesMap().get(CNEE + "version"))
                && "weaveworksdemos/front-end".equals(e.getEntityDiscovered().getResourceName()));
        assertThat(events).anyMatch(e -> e.getEventCase() == SensorEvent.EventCase.ENTITY_DISCOVERED
                && e.getEntityDiscovered().getOntologyType().equals(CNEE + "ImageRegistry"));
        assertThat(events).anyMatch(e -> e.getEventCase() == SensorEvent.EventCase.RELATIONSHIP_ASSERTED
                && e.getRelationshipAsserted().getPredicate().equals(CNEE + "pullsImageFrom"));
        assertThat(events).anyMatch(e -> e.getEventCase() == SensorEvent.EventCase.RELATIONSHIP_ASSERTED
                && e.getRelationshipAsserted().getPredicate().equals(CNEE + "contains"));
        assertThat(events).anyMatch(e -> e.getEventCase() == SensorEvent.EventCase.RELATIONSHIP_ASSERTED
                && e.getRelationshipAsserted().getPredicate().equals(CNEE + "usesImage"));
    }
}
