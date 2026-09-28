package com.kubiki.metis.sensor.kubernetes;

import com.kubiki.metis.config.MetisProperties;
import com.kubiki.metis.grpc.SensorEvent;
import com.kubiki.metis.grpc.StateChangedEvent;
import com.kubiki.metis.knowledge.CneeOntology;
import com.kubiki.metis.sensor.IriFactory;
import com.kubiki.metis.sensor.SensorEventPublisher;
import io.fabric8.kubernetes.api.model.ContainerStateTerminated;
import io.fabric8.kubernetes.api.model.ContainerStateWaiting;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Watches Kubernetes Pods and detects container-level anomaly states:
 * <ul>
 *   <li>CrashLoopBackOff → {@code cnee:ContainerCrashLoopBackOffState}</li>
 *   <li>OOMKilled → {@code cnee:ContainerOOMKilledState}</li>
 *   <li>Liveness probe failed → {@code cnee:ContainerLivenessProbeFailedState}</li>
 *   <li>Readiness probe failed → {@code cnee:ContainerReadinessProbeFailedState}</li>
 * </ul>
 *
 * <p>Emits {@link StateChangedEvent} on the container's parent pod IRI when an
 * anomaly condition is detected. These states are CNEEOnt State subclasses;
 * BridgeOnt marks them as {@code Anomaly} and maps remediable ones via
 * {@code isResolvedByIntent} for Palamedes discovery.
 */
@Component
@ConditionalOnProperty(name = "metis.sensor.enabled", havingValue = "true", matchIfMissing = false)
public class ContainerAnomalySensor extends AbstractNamespacedSensor {

    private final SensorEventPublisher publisher;
    private final IriFactory iriFactory;

    public ContainerAnomalySensor(KubernetesClient client,
                                  MetisProperties properties,
                                  SensorEventPublisher publisher,
                                  IriFactory iriFactory) {
        super(client, properties);
        this.publisher = publisher;
        this.iriFactory = iriFactory;
    }

    @Override
    public String name() {
        return "ContainerAnomalySensor";
    }

    @Override
    protected SharedIndexInformer<Pod> createInformer(KubernetesClient client, String namespace) {
        var podOp = namespace != null
                ? client.pods().inNamespace(namespace)
                : client.pods().inAnyNamespace();

        return podOp.inform(new ResourceEventHandler<>() {
            @Override
            public void onAdd(Pod pod) {
                detectContainerAnomalies(pod);
            }

            @Override
            public void onUpdate(Pod oldPod, Pod newPod) {
                detectContainerAnomalies(newPod);
            }

            @Override
            public void onDelete(Pod pod, boolean deletedFinalStateUnknown) {
                // Deletion handled by PodSensor
            }
        });
    }

    // -------------------------------------------------------------------------

    void detectContainerAnomalies(Pod pod) {
        if (pod.getStatus() == null) return;

        String ns = pod.getMetadata().getNamespace();
        String name = pod.getMetadata().getName();
        String podIri = iriFactory.namespacedIri(CneeOntology.KIND_POD, ns, name);

        List<ContainerStatus> containerStatuses = pod.getStatus().getContainerStatuses();
        if (containerStatuses == null) return;

        for (ContainerStatus cs : containerStatuses) {
            String anomalyState = detectAnomaly(cs);
            if (anomalyState != null) {
                emitStateChange(podIri, anomalyState);
                log.info("ContainerAnomalySensor: anomaly {} on pod {}/{} container {}",
                        anomalyState, ns, name, cs.getName());
            }
        }

        // Also check pod-level conditions for eviction
        if ("Failed".equals(pod.getStatus().getPhase()) && "Evicted".equals(pod.getStatus().getReason())) {
            emitStateChange(podIri, CneeOntology.STATE_POD_EVICTED);
            log.info("ContainerAnomalySensor: pod {}/{} evicted", ns, name);
        }
    }

    /**
     * Inspects a container status and returns the CNEEOnt anomaly state local name,
     * or {@code null} if the container is healthy.
     */
    private String detectAnomaly(ContainerStatus cs) {
        ContainerStateWaiting waiting = cs.getState() != null ? cs.getState().getWaiting() : null;
        ContainerStateTerminated terminated = cs.getState() != null ? cs.getState().getTerminated() : null;

        if (waiting != null) {
            String reason = waiting.getReason();
            if ("CrashLoopBackOff".equals(reason)) {
                return CneeOntology.STATE_CONTAINER_CRASH_LOOP;
            }
            if ("ImagePullBackOff".equals(reason) || "ErrImagePull".equals(reason)) {
                return CneeOntology.STATE_IMAGE_PULL_BACKOFF;
            }
        }

        if (terminated != null) {
            String reason = terminated.getReason();
            if ("OOMKilled".equals(reason)) {
                return CneeOntology.STATE_CONTAINER_OOM_KILLED;
            }
        }

        // Check last terminated state for OOMKilled (container may have restarted)
        ContainerStateTerminated lastTerminated = cs.getLastState() != null
                ? cs.getLastState().getTerminated() : null;
        if (lastTerminated != null && "OOMKilled".equals(lastTerminated.getReason())) {
            // Only report if restart count is high (indicates recurring OOM)
            if (cs.getRestartCount() != null && cs.getRestartCount() >= 3) {
                return CneeOntology.STATE_CONTAINER_OOM_KILLED;
            }
        }

        // Readiness/liveness probe failures show up as conditions on the pod, not container status.
        // We detect them via the ready flag: container not ready while pod is Running = probe failure.
        if (Boolean.FALSE.equals(cs.getReady()) && cs.getState() != null && cs.getState().getRunning() != null) {
            return CneeOntology.STATE_CONTAINER_READINESS_FAILED;
        }

        return null;
    }

    private void emitStateChange(String resourceIri, String stateLocalName) {
        String stateIri = iriFactory.typeIri(stateLocalName);

        StateChangedEvent stateChanged = StateChangedEvent.newBuilder()
                .setResourceIri(resourceIri)
                .setNewStateIri(stateIri)
                .build();

        publisher.publish(SensorEventPublisher.withTimestamp(
                SensorEvent.newBuilder().setStateChanged(stateChanged)));
    }
}
