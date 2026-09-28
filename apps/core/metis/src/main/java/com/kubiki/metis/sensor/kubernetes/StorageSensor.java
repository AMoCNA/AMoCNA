package com.kubiki.metis.sensor.kubernetes;

import com.kubiki.metis.config.MetisProperties;
import com.kubiki.metis.grpc.EntityDeletedEvent;
import com.kubiki.metis.grpc.EntityDiscoveredEvent;
import com.kubiki.metis.grpc.RelationshipAssertedEvent;
import com.kubiki.metis.grpc.SensorEvent;
import com.kubiki.metis.knowledge.CneeOntology;
import com.kubiki.metis.sensor.IriFactory;
import com.kubiki.metis.sensor.SensorEventPublisher;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Watches PersistentVolumeClaims and Pod volumes to detect storage relationships:
 * <ul>
 *   <li>{@link EntityDiscoveredEvent} — for PVCs</li>
 *   <li>{@code cnee:usesStorage(pod, pvc)} — when a pod references a PVC</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "metis.sensor.enabled", havingValue = "true", matchIfMissing = false)
public class StorageSensor extends AbstractNamespacedSensor {

    private final SensorEventPublisher publisher;
    private final IriFactory iriFactory;
    private final String cneeNamespace;

    public StorageSensor(KubernetesClient client,
                         MetisProperties properties,
                         SensorEventPublisher publisher,
                         IriFactory iriFactory) {
        super(client, properties);
        this.publisher = publisher;
        this.iriFactory = iriFactory;
        this.cneeNamespace = properties.ontology().cneeNamespace();
    }

    @Override
    public String name() {
        return "StorageSensor";
    }

    @Override
    protected SharedIndexInformer<PersistentVolumeClaim> createInformer(KubernetesClient client, String namespace) {
        var pvcOp = namespace != null
                ? client.persistentVolumeClaims().inNamespace(namespace)
                : client.persistentVolumeClaims().inAnyNamespace();

        return pvcOp.inform(new ResourceEventHandler<>() {
            @Override
            public void onAdd(PersistentVolumeClaim pvc) {
                onPvcAdded(pvc);
            }

            @Override
            public void onUpdate(PersistentVolumeClaim oldPvc, PersistentVolumeClaim newPvc) {
                // Metadata updates handled by generic update logic if needed
            }

            @Override
            public void onDelete(PersistentVolumeClaim pvc, boolean deletedFinalStateUnknown) {
                onPvcDeleted(pvc);
            }
        });
    }

    private void onPvcAdded(PersistentVolumeClaim pvc) {
        String ns = pvc.getMetadata().getNamespace();
        String name = pvc.getMetadata().getName();
        String iri = iriFactory.namespacedIri(CneeOntology.KIND_PVC, ns, name);
        String type = iriFactory.typeIri(CneeOntology.CLASS_PERSISTENT_STORAGE);

        EntityDiscoveredEvent discovered = EntityDiscoveredEvent.newBuilder()
                .setResourceIri(iri)
                .setOntologyType(type)
                .setResourceId(name)
                .setResourceName(name)
                .build();

        publisher.publish(SensorEventPublisher.withTimestamp(
                SensorEvent.newBuilder().setEntityDiscovered(discovered)));

        log.debug("StorageSensor: added PVC {}/{}", ns, name);

        // Find pods using this PVC and link them
        // Note: In a fully event-driven system, PodSensor would handle this, 
        // but adding it here ensures existing links are caught if PVC arrives after Pod.
        reconcilePodStorageLinks(ns, name, iri);
    }

    private void onPvcDeleted(PersistentVolumeClaim pvc) {
        String ns = pvc.getMetadata().getNamespace();
        String name = pvc.getMetadata().getName();
        String iri = iriFactory.namespacedIri(CneeOntology.KIND_PVC, ns, name);

        EntityDeletedEvent deleted = EntityDeletedEvent.newBuilder()
                .setResourceIri(iri)
                .setOntologyType(iriFactory.typeIri(CneeOntology.CLASS_PERSISTENT_STORAGE))
                .build();

        publisher.publish(SensorEventPublisher.withTimestamp(
                SensorEvent.newBuilder().setEntityDeleted(deleted)));

        log.debug("StorageSensor: deleted PVC {}/{}", ns, name);
    }

    private void reconcilePodStorageLinks(String namespace, String pvcName, String pvcIri) {
        List<Pod> pods = client.pods().inNamespace(namespace).list().getItems();
        for (Pod pod : pods) {
            if (pod.getSpec() != null && pod.getSpec().getVolumes() != null) {
                for (Volume vol : pod.getSpec().getVolumes()) {
                    if (vol.getPersistentVolumeClaim() != null && pvcName.equals(vol.getPersistentVolumeClaim().getClaimName())) {
                        String podIri = iriFactory.namespacedIri(CneeOntology.KIND_POD, namespace, pod.getMetadata().getName());
                        emitRelationship(podIri, CneeOntology.PROP_USES_STORAGE, pvcIri);
                    }
                }
            }
        }
    }

    private void emitRelationship(String subjectIri, String predicateLocalName, String objectIri) {
        RelationshipAssertedEvent rel = RelationshipAssertedEvent.newBuilder()
                .setSubjectIri(subjectIri)
                .setPredicate(cneeNamespace + predicateLocalName)
                .setObjectIri(objectIri)
                .build();

        publisher.publish(SensorEventPublisher.withTimestamp(
                SensorEvent.newBuilder().setRelationshipAsserted(rel)));
    }
}
