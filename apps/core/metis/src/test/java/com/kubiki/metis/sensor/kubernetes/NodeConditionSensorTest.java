package com.kubiki.metis.sensor.kubernetes;

import com.kubiki.metis.config.MetisProperties;
import com.kubiki.metis.grpc.SensorEvent;
import com.kubiki.metis.knowledge.CneeOntology;
import com.kubiki.metis.sensor.IriFactory;
import com.kubiki.metis.sensor.SensorEventPublisher;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.NodeBuilder;
import io.fabric8.kubernetes.api.model.NodeConditionBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class NodeConditionSensorTest {

    private static final String CNEE = "http://www.semanticweb.org/szymo/ontologies/2026/2/CNEEOnt/";

    private SensorEventPublisher publisher;
    private NodeConditionSensor sensor;

    private static Node nodeWithCondition(String name, String type, String status) {
        return new NodeBuilder()
                .withNewMetadata().withName(name).endMetadata()
                .withNewStatus()
                .addToConditions(new NodeConditionBuilder()
                        .withType(type).withStatus(status).build())
                .endStatus()
                .build();
    }

    @BeforeEach
    void setUp() {
        MetisProperties props = new MetisProperties(
                new MetisProperties.GraphDB("http://x", "test", 1000),
                new MetisProperties.Ontology(CNEE),
                new MetisProperties.Sensor(true, List.of(), 50, 500));
        IriFactory iriFactory = new IriFactory(props);
        publisher = mock(SensorEventPublisher.class);
        sensor = new NodeConditionSensor(mock(KubernetesClient.class), publisher, iriFactory);
    }

    @Test
    void detectsNodeNotReady() {
        Node node = nodeWithCondition("worker-1", "Ready", "False");
        sensor.detectNodeAnomalies(node);

        ArgumentCaptor<SensorEvent> captor = ArgumentCaptor.forClass(SensorEvent.class);
        verify(publisher).publish(captor.capture());
        assertThat(captor.getValue().getStateChanged().getNewStateIri())
                .isEqualTo(CNEE + CneeOntology.STATE_NODE_NOT_READY);
        assertThat(captor.getValue().getStateChanged().getResourceIri())
                .isEqualTo(CNEE + "Node_worker-1");
    }

    @Test
    void detectsMemoryPressure() {
        Node node = nodeWithCondition("worker-2", "MemoryPressure", "True");
        sensor.detectNodeAnomalies(node);

        ArgumentCaptor<SensorEvent> captor = ArgumentCaptor.forClass(SensorEvent.class);
        verify(publisher).publish(captor.capture());
        assertThat(captor.getValue().getStateChanged().getNewStateIri())
                .isEqualTo(CNEE + CneeOntology.STATE_NODE_MEMORY_STARVED);
    }

    @Test
    void healthyNodeEmitsNothing() {
        Node node = new NodeBuilder()
                .withNewMetadata().withName("healthy-node").endMetadata()
                .withNewStatus()
                .addToConditions(new NodeConditionBuilder()
                        .withType("Ready").withStatus("True").build())
                .addToConditions(new NodeConditionBuilder()
                        .withType("MemoryPressure").withStatus("False").build())
                .endStatus()
                .build();

        sensor.detectNodeAnomalies(node);
        verifyNoInteractions(publisher);
    }

    @Test
    void nodeWithNoStatusEmitsNothing() {
        Node node = new NodeBuilder()
                .withNewMetadata().withName("no-status").endMetadata()
                .build();
        sensor.detectNodeAnomalies(node);
        verifyNoInteractions(publisher);
    }

    // -------------------------------------------------------------------------

    @Test
    void multipleAnomaliesEmitMultipleEvents() {
        Node node = new NodeBuilder()
                .withNewMetadata().withName("sick-node").endMetadata()
                .withNewStatus()
                .addToConditions(new NodeConditionBuilder()
                        .withType("Ready").withStatus("False").build())
                .addToConditions(new NodeConditionBuilder()
                        .withType("MemoryPressure").withStatus("True").build())
                .endStatus()
                .build();

        sensor.detectNodeAnomalies(node);
        verify(publisher, times(2)).publish(any());
    }
}
