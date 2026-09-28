package com.kubiki.metis.sensor.kubernetes;

import com.kubiki.metis.config.MetisProperties;
import com.kubiki.metis.grpc.SensorEvent;
import com.kubiki.metis.knowledge.CneeOntology;
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
import static org.mockito.Mockito.*;

class ContainerAnomalySensorTest {

    private static final String CNEE = "http://www.semanticweb.org/szymo/ontologies/2026/2/CNEEOnt/";

    private SensorEventPublisher publisher;
    private ContainerAnomalySensor sensor;

    private static Pod podWithWaitingReason(String name, String ns, String reason) {
        return new PodBuilder()
                .withNewMetadata().withName(name).withNamespace(ns).endMetadata()
                .withNewStatus()
                .addNewContainerStatus()
                .withName("app")
                .withReady(false)
                .withNewState().withNewWaiting().withReason(reason).endWaiting().endState()
                .endContainerStatus()
                .endStatus()
                .build();
    }

    private static Pod podWithTerminatedReason(String name, String ns, String reason) {
        return new PodBuilder()
                .withNewMetadata().withName(name).withNamespace(ns).endMetadata()
                .withNewStatus()
                .addNewContainerStatus()
                .withName("app")
                .withReady(false)
                .withNewState().withNewTerminated().withReason(reason).endTerminated().endState()
                .endContainerStatus()
                .endStatus()
                .build();
    }

    private static Pod podWithRunningButNotReady(String name, String ns) {
        return new PodBuilder()
                .withNewMetadata().withName(name).withNamespace(ns).endMetadata()
                .withNewStatus()
                .addNewContainerStatus()
                .withName("app")
                .withReady(false)
                .withNewState().withNewRunning().endRunning().endState()
                .endContainerStatus()
                .endStatus()
                .build();
    }

    private static Pod evictedPod(String name, String ns) {
        return new PodBuilder()
                .withNewMetadata().withName(name).withNamespace(ns).endMetadata()
                .withNewStatus()
                .withPhase("Failed")
                .withReason("Evicted")
                .endStatus()
                .build();
    }

    private static Pod healthyPod(String name, String ns) {
        return new PodBuilder()
                .withNewMetadata().withName(name).withNamespace(ns).endMetadata()
                .withNewStatus()
                .withPhase("Running")
                .addNewContainerStatus()
                .withName("app")
                .withReady(true)
                .withNewState().withNewRunning().endRunning().endState()
                .endContainerStatus()
                .endStatus()
                .build();
    }

    @BeforeEach
    void setUp() {
        MetisProperties props = new MetisProperties(
                new MetisProperties.GraphDB("http://x", "test", 1000),
                new MetisProperties.Ontology(CNEE),
                new MetisProperties.Sensor(true, List.of("ns"), 50, 500));
        IriFactory iriFactory = new IriFactory(props);
        publisher = mock(SensorEventPublisher.class);
        sensor = new ContainerAnomalySensor(mock(KubernetesClient.class), props, publisher, iriFactory);
    }

    @Test
    void detectsCrashLoopBackOff() {
        Pod pod = podWithWaitingReason("my-pod", "ns", "CrashLoopBackOff");
        sensor.detectContainerAnomalies(pod);

        ArgumentCaptor<SensorEvent> captor = ArgumentCaptor.forClass(SensorEvent.class);
        verify(publisher).publish(captor.capture());
        assertThat(captor.getValue().getStateChanged().getNewStateIri())
                .isEqualTo(CNEE + CneeOntology.STATE_CONTAINER_CRASH_LOOP);
    }

    // -------------------------------------------------------------------------

    @Test
    void detectsOOMKilled() {
        Pod pod = podWithTerminatedReason("oom-pod", "ns", "OOMKilled");
        sensor.detectContainerAnomalies(pod);

        ArgumentCaptor<SensorEvent> captor = ArgumentCaptor.forClass(SensorEvent.class);
        verify(publisher).publish(captor.capture());
        assertThat(captor.getValue().getStateChanged().getNewStateIri())
                .isEqualTo(CNEE + CneeOntology.STATE_CONTAINER_OOM_KILLED);
    }

    @Test
    void detectsReadinessProbeFailure() {
        Pod pod = podWithRunningButNotReady("probe-pod", "ns");
        sensor.detectContainerAnomalies(pod);

        ArgumentCaptor<SensorEvent> captor = ArgumentCaptor.forClass(SensorEvent.class);
        verify(publisher).publish(captor.capture());
        assertThat(captor.getValue().getStateChanged().getNewStateIri())
                .isEqualTo(CNEE + CneeOntology.STATE_CONTAINER_READINESS_FAILED);
    }

    @Test
    void detectsEvictedPod() {
        Pod pod = evictedPod("evicted-pod", "ns");
        sensor.detectContainerAnomalies(pod);

        ArgumentCaptor<SensorEvent> captor = ArgumentCaptor.forClass(SensorEvent.class);
        verify(publisher).publish(captor.capture());
        assertThat(captor.getValue().getStateChanged().getNewStateIri())
                .isEqualTo(CNEE + CneeOntology.STATE_POD_EVICTED);
    }

    @Test
    void healthyContainerEmitsNothing() {
        Pod pod = healthyPod("ok-pod", "ns");
        sensor.detectContainerAnomalies(pod);
        verifyNoInteractions(publisher);
    }

    @Test
    void podWithNoStatusEmitsNothing() {
        Pod pod = new PodBuilder()
                .withNewMetadata().withName("no-status").withNamespace("ns").endMetadata()
                .build();
        sensor.detectContainerAnomalies(pod);
        verifyNoInteractions(publisher);
    }
}
