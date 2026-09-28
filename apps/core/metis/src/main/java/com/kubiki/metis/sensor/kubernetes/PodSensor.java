package com.kubiki.metis.sensor.kubernetes;

import com.kubiki.metis.config.MetisProperties;
import com.kubiki.metis.grpc.*;
import com.kubiki.metis.knowledge.CneeOntology;
import com.kubiki.metis.sensor.IriFactory;
import com.kubiki.metis.sensor.SensorEventPublisher;
import io.fabric8.kubernetes.api.model.ContainerStateWaiting;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;

/**
 * Watches Kubernetes Pods and emits:
 * <ul>
 *   <li>{@link EntityDiscoveredEvent} — on pod add</li>
 *   <li>{@link StateChangedEvent} — on pod phase change</li>
 *   <li>{@link EntityDeletedEvent} — on pod delete</li>
 * </ul>
 *
 * <p>CNEEOnt type: {@code cnee:ExecutionUnit}
 */
@Component
@ConditionalOnProperty(name = "metis.sensor.enabled", havingValue = "true", matchIfMissing = false)
public class PodSensor extends AbstractNamespacedSensor {

    private static final String ONTOLOGY_TYPE_LOCAL = CneeOntology.CLASS_EXECUTION_UNIT;

    // Kubernetes phase → CNEEOnt state local name
    private static final Map<String, String> PHASE_TO_STATE = Map.of(
            "Pending", CneeOntology.STATE_PENDING,
            "Running", CneeOntology.STATE_RUNNING,
            "Failed", CneeOntology.STATE_FAILED,
            "Succeeded", CneeOntology.STATE_SUCCEEDED,
            "Unknown", CneeOntology.STATE_UNKNOWN
    );

    private final SensorEventPublisher publisher;
    private final IriFactory iriFactory;
    private final String cneeNamespace;

    public PodSensor(KubernetesClient client,
                     MetisProperties properties,
                     SensorEventPublisher publisher,
                     IriFactory iriFactory) {
        super(client, properties);
        this.publisher = publisher;
        this.iriFactory = iriFactory;
        this.cneeNamespace = properties.ontology().cneeNamespace();
    }

    private static String podPhase(Pod pod) {
        if (pod.getStatus() == null) return null;
        return pod.getStatus().getPhase();
    }

    /**
     * Resolve the state to emit for the pod.
     *
     * <p>For phase=Pending, prefer explicit pull anomaly over generic pending.
     * This prevents PodSensor from overwriting container anomaly state on add/update races.
     */
    private static String resolveStateLocalName(Pod pod, String phase) {
        if (!"Pending".equals(phase) || pod.getStatus() == null) {
            return PHASE_TO_STATE.getOrDefault(phase, CneeOntology.STATE_UNKNOWN);
        }
        var statuses = pod.getStatus().getContainerStatuses();
        if (statuses != null) {
            for (ContainerStatus cs : statuses) {
                ContainerStateWaiting waiting =
                        cs.getState() != null ? cs.getState().getWaiting() : null;
                if (waiting == null) continue;
                String reason = waiting.getReason();
                if ("ImagePullBackOff".equals(reason) || "ErrImagePull".equals(reason)) {
                    return CneeOntology.STATE_IMAGE_PULL_BACKOFF;
                }
            }
        }
        return CneeOntology.STATE_PENDING;
    }

    @Override
    public String name() {
        return "PodSensor";
    }

    // -------------------------------------------------------------------------
    // Event handlers — package-visible to allow direct unit testing.
    // -------------------------------------------------------------------------

    @Override
    protected SharedIndexInformer<Pod> createInformer(KubernetesClient client, String namespace) {
        var podOp = namespace != null
                ? client.pods().inNamespace(namespace)
                : client.pods().inAnyNamespace();

        SharedIndexInformer<Pod> informer = podOp.inform(new ResourceEventHandler<>() {
            @Override
            public void onAdd(Pod pod) {
                onPodAdded(pod);
            }

            @Override
            public void onUpdate(Pod oldPod, Pod newPod) {
                onPodUpdated(oldPod, newPod);
            }

            @Override
            public void onDelete(Pod pod, boolean deletedFinalStateUnknown) {
                onPodDeleted(pod);
            }
        });

        return informer;
    }

    void onPodAdded(Pod pod) {
        String ns = pod.getMetadata().getNamespace();
        String name = pod.getMetadata().getName();
        String iri = iriFactory.namespacedIri(CneeOntology.KIND_POD, ns, name);
        String type = iriFactory.typeIri(ONTOLOGY_TYPE_LOCAL);

        EntityDiscoveredEvent discovered = EntityDiscoveredEvent.newBuilder()
                .setResourceIri(iri)
                .setOntologyType(type)
                .setResourceId(name)
                .setResourceName(name)
                .putProperties(cneeNamespace + CneeOntology.PROP_NAMESPACE, ns)
                .build();

        publisher.publish(SensorEventPublisher.withTimestamp(
                SensorEvent.newBuilder().setEntityDiscovered(discovered)));

        // Discover relationships from ownerReferences
        if (pod.getMetadata().getOwnerReferences() != null) {
            for (OwnerReference owner : pod.getMetadata().getOwnerReferences()) {
                String ownerIri = iriFactory.namespacedIri(owner.getKind(), ns, owner.getName());
                RelationshipAssertedEvent rel = RelationshipAssertedEvent.newBuilder()
                        .setSubjectIri(iri)
                        .setPredicate(cneeNamespace + CneeOntology.PROP_IS_PART_OF)
                        .setObjectIri(ownerIri)
                        .build();
                publisher.publish(SensorEventPublisher.withTimestamp(
                        SensorEvent.newBuilder().setRelationshipAsserted(rel)));
            }
        }

        // Also emit initial state if phase is known
        String phase = podPhase(pod);
        if (phase != null) {
            emitStateChange(iri, pod, phase, null);
        }

        log.debug("PodSensor: added pod {}/{}", ns, name);
    }

    void onPodUpdated(Pod oldPod, Pod newPod) {
        String oldPhase = podPhase(oldPod);
        String newPhase = podPhase(newPod);

        String ns = newPod.getMetadata().getNamespace();
        String name = newPod.getMetadata().getName();
        String iri = iriFactory.namespacedIri(CneeOntology.KIND_POD, ns, name);

        if (newPhase != null && !newPhase.equals(oldPhase)) {
            emitStateChange(iri, newPod, newPhase, oldPhase);
            log.debug("PodSensor: state change {}/{} {} → {}", ns, name, oldPhase, newPhase);
        }

        if (!Objects.equals(oldPod.getMetadata().getOwnerReferences(), newPod.getMetadata().getOwnerReferences())) {
            if (newPod.getMetadata().getOwnerReferences() != null) {
                for (OwnerReference owner : newPod.getMetadata().getOwnerReferences()) {
                    String ownerIri = iriFactory.namespacedIri(owner.getKind(), ns, owner.getName());
                    RelationshipAssertedEvent rel = RelationshipAssertedEvent.newBuilder()
                            .setSubjectIri(iri)
                            .setPredicate(cneeNamespace + CneeOntology.PROP_IS_PART_OF)
                            .setObjectIri(ownerIri)
                            .build();
                    publisher.publish(SensorEventPublisher.withTimestamp(
                            SensorEvent.newBuilder().setRelationshipAsserted(rel)));
                }
            }
        }
    }

    void onPodDeleted(Pod pod) {
        String ns = pod.getMetadata().getNamespace();
        String name = pod.getMetadata().getName();
        String iri = iriFactory.namespacedIri(CneeOntology.KIND_POD, ns, name);
        String type = iriFactory.typeIri(ONTOLOGY_TYPE_LOCAL);

        EntityDeletedEvent deleted = EntityDeletedEvent.newBuilder()
                .setResourceIri(iri)
                .setOntologyType(type)
                .build();

        publisher.publish(SensorEventPublisher.withTimestamp(
                SensorEvent.newBuilder().setEntityDeleted(deleted)));

        log.debug("PodSensor: deleted pod {}/{}", ns, name);
    }

    private void emitStateChange(String resourceIri, Pod pod, String phase, String previousPhase) {
        String stateLocalName = resolveStateLocalName(pod, phase);
        String newStateIri = iriFactory.typeIri(stateLocalName);

        StateChangedEvent.Builder builder = StateChangedEvent.newBuilder()
                .setResourceIri(resourceIri)
                .setNewStateIri(newStateIri);

        if (previousPhase != null) {
            String prevLocalName = PHASE_TO_STATE.getOrDefault(previousPhase, CneeOntology.STATE_UNKNOWN);
            builder.setPreviousStateIri(iriFactory.typeIri(prevLocalName));
        }

        publisher.publish(SensorEventPublisher.withTimestamp(
                SensorEvent.newBuilder().setStateChanged(builder.build())));
    }
}
