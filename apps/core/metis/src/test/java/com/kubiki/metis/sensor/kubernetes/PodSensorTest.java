package com.kubiki.metis.sensor.kubernetes;

import com.kubiki.metis.config.MetisProperties;
import com.kubiki.metis.grpc.SensorEvent;
import com.kubiki.metis.sensor.IriFactory;
import com.kubiki.metis.sensor.SensorEventPublisher;
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

/**
 * Unit tests for {@link PodSensor} — directly invokes the event handlers
 * with hand-built {@link Pod} POJOs (no mock cluster needed).
 *
 * <p>Verifies the Kubernetes phase → CNEEOnt state mapping and the IRI scheme
 * used for pod events.
 */
class PodSensorTest {

    private static final String CNEE = "http://www.semanticweb.org/szymo/ontologies/2026/2/CNEEOnt/";

    private SensorEventPublisher publisher;
    private PodSensor sensor;

    private static Pod pod(String name, String namespace, String phase) {
        return new PodBuilder()
                .withNewMetadata().withName(name).withNamespace(namespace).endMetadata()
                .withNewStatus().withPhase(phase).endStatus()
                .build();
    }

    @BeforeEach
    void setUp() {
        MetisProperties props = new MetisProperties(
                new MetisProperties.GraphDB("http://x", "test", 1000),
                new MetisProperties.Ontology(CNEE),
                new MetisProperties.Sensor(true, List.of("test-ns"), 50, 500));

        IriFactory iriFactory = new IriFactory(props);
        publisher = mock(SensorEventPublisher.class);
        // KubernetesClient is unused by event handlers — only by start()/stop()
        sensor = new PodSensor(mock(KubernetesClient.class), props, publisher, iriFactory);
    }

    @Test
    void onPodAdded_emitsEntityDiscoveredAndStateChanged() {
        Pod pod = pod("my-pod", "test-ns", "Running");

        sensor.onPodAdded(pod);

        ArgumentCaptor<SensorEvent> captor = ArgumentCaptor.forClass(SensorEvent.class);
        verify(publisher, org.mockito.Mockito.times(2)).publish(captor.capture());

        List<SensorEvent> events = captor.getAllValues();

        assertThat(events.get(0).getEventCase()).isEqualTo(SensorEvent.EventCase.ENTITY_DISCOVERED);
        assertThat(events.get(0).getEntityDiscovered().getResourceIri())
                .isEqualTo(CNEE + "Pod_test-ns_my-pod");
        assertThat(events.get(0).getEntityDiscovered().getOntologyType())
                .isEqualTo(CNEE + "ExecutionUnit");

        assertThat(events.get(1).getEventCase()).isEqualTo(SensorEvent.EventCase.STATE_CHANGED);
        assertThat(events.get(1).getStateChanged().getNewStateIri())
                .isEqualTo(CNEE + "ExecutionUnitRunning");
    }

    @Test
    void onPodAdded_pendingPhaseMapsToExecutionUnitPending() {
        sensor.onPodAdded(pod("p1", "ns", "Pending"));

        ArgumentCaptor<SensorEvent> captor = ArgumentCaptor.forClass(SensorEvent.class);
        verify(publisher, org.mockito.Mockito.times(2)).publish(captor.capture());

        SensorEvent stateChanged = captor.getAllValues().get(1);
        assertThat(stateChanged.getStateChanged().getNewStateIri())
                .isEqualTo(CNEE + "ExecutionUnitPending");
    }

    @Test
    void onPodAdded_failedPhaseMapsToExecutionUnitFailed() {
        sensor.onPodAdded(pod("p2", "ns", "Failed"));

        ArgumentCaptor<SensorEvent> captor = ArgumentCaptor.forClass(SensorEvent.class);
        verify(publisher, org.mockito.Mockito.times(2)).publish(captor.capture());

        assertThat(captor.getAllValues().get(1).getStateChanged().getNewStateIri())
                .isEqualTo(CNEE + "ExecutionUnitFailed");
    }

    @Test
    void onPodAdded_succeededPhaseMapsToExecutionUnitSucceeded() {
        sensor.onPodAdded(pod("p3", "ns", "Succeeded"));

        ArgumentCaptor<SensorEvent> captor = ArgumentCaptor.forClass(SensorEvent.class);
        verify(publisher, org.mockito.Mockito.times(2)).publish(captor.capture());

        assertThat(captor.getAllValues().get(1).getStateChanged().getNewStateIri())
                .isEqualTo(CNEE + "ExecutionUnitSucceeded");
    }

    @Test
    void onPodAdded_unknownPhaseMapsToCneeUnknown() {
        sensor.onPodAdded(pod("p4", "ns", "SomeWeirdPhase"));

        ArgumentCaptor<SensorEvent> captor = ArgumentCaptor.forClass(SensorEvent.class);
        verify(publisher, org.mockito.Mockito.times(2)).publish(captor.capture());

        assertThat(captor.getAllValues().get(1).getStateChanged().getNewStateIri())
                .isEqualTo(CNEE + "ExecutionUnitUnknown");
    }

    @Test
    void onPodUpdated_emitsStateChangeOnlyWhenPhaseDiffers() {
        Pod pending = pod("p", "ns", "Pending");
        Pod running = pod("p", "ns", "Running");

        sensor.onPodUpdated(pending, running);

        ArgumentCaptor<SensorEvent> captor = ArgumentCaptor.forClass(SensorEvent.class);
        verify(publisher).publish(captor.capture());

        SensorEvent event = captor.getValue();
        assertThat(event.getEventCase()).isEqualTo(SensorEvent.EventCase.STATE_CHANGED);
        assertThat(event.getStateChanged().getNewStateIri())
                .isEqualTo(CNEE + "ExecutionUnitRunning");
        assertThat(event.getStateChanged().getPreviousStateIri())
                .isEqualTo(CNEE + "ExecutionUnitPending");
    }

    @Test
    void onPodUpdated_skipsWhenPhaseUnchanged() {
        Pod a = pod("p", "ns", "Running");
        Pod b = pod("p", "ns", "Running");

        sensor.onPodUpdated(a, b);

        org.mockito.Mockito.verifyNoInteractions(publisher);
    }

    // -------------------------------------------------------------------------

    @Test
    void onPodDeleted_emitsEntityDeleted() {
        sensor.onPodDeleted(pod("doomed", "ns", "Running"));

        ArgumentCaptor<SensorEvent> captor = ArgumentCaptor.forClass(SensorEvent.class);
        verify(publisher).publish(captor.capture());

        SensorEvent event = captor.getValue();
        assertThat(event.getEventCase()).isEqualTo(SensorEvent.EventCase.ENTITY_DELETED);
        assertThat(event.getEntityDeleted().getResourceIri())
                .isEqualTo(CNEE + "Pod_ns_doomed");
        assertThat(event.getEntityDeleted().getOntologyType())
                .isEqualTo(CNEE + "ExecutionUnit");
    }
}
