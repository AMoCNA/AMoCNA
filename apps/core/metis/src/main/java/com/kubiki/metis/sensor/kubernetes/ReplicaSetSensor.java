package com.kubiki.metis.sensor.kubernetes;

import com.kubiki.metis.config.MetisProperties;
import com.kubiki.metis.grpc.EntityDeletedEvent;
import com.kubiki.metis.grpc.EntityDiscoveredEvent;
import com.kubiki.metis.grpc.RelationshipAssertedEvent;
import com.kubiki.metis.grpc.SensorEvent;
import com.kubiki.metis.knowledge.CneeOntology;
import com.kubiki.metis.sensor.IriFactory;
import com.kubiki.metis.sensor.SensorEventPublisher;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.apps.ReplicaSet;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Watches Kubernetes ReplicaSets and completes the Pod → ReplicaSet → Deployment
 * owner chain.
 *
 * <p>{@link PodSensor} already asserts {@code Pod isPartOf ReplicaSet} from the
 * pod's owner references. This sensor adds the missing link by emitting the
 * ReplicaSet entity and asserting {@code ReplicaSet isPartOf Deployment} from the
 * ReplicaSet's owner references, so Palamedes can traverse {@code isPartOf+} from a
 * failing Pod up to the owning Deployment.
 */
@Component
@ConditionalOnProperty(name = "metis.sensor.enabled", havingValue = "true", matchIfMissing = false)
public class ReplicaSetSensor extends AbstractNamespacedSensor {

    private final SensorEventPublisher publisher;
    private final IriFactory iriFactory;
    private final String cneeNamespace;

    public ReplicaSetSensor(KubernetesClient client,
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
        return "ReplicaSetSensor";
    }

    @Override
    protected SharedIndexInformer<ReplicaSet> createInformer(KubernetesClient client, String namespace) {
        var rsOp = namespace != null
                ? client.apps().replicaSets().inNamespace(namespace)
                : client.apps().replicaSets().inAnyNamespace();

        return rsOp.inform(new ResourceEventHandler<>() {
            @Override
            public void onAdd(ReplicaSet rs) {
                onReplicaSetAdded(rs);
            }

            @Override
            public void onUpdate(ReplicaSet oldRs, ReplicaSet newRs) {
                onReplicaSetAdded(newRs);
            }

            @Override
            public void onDelete(ReplicaSet rs, boolean deletedFinalStateUnknown) {
                onReplicaSetDeleted(rs);
            }
        });
    }

    void onReplicaSetAdded(ReplicaSet rs) {
        if (rs.getMetadata() == null) {
            return;
        }
        String ns = rs.getMetadata().getNamespace();
        String name = rs.getMetadata().getName();
        String iri = iriFactory.namespacedIri(CneeOntology.KIND_REPLICSET, ns, name);

        EntityDiscoveredEvent discovered = EntityDiscoveredEvent.newBuilder()
                .setResourceIri(iri)
                .setOntologyType(iriFactory.typeIri(CneeOntology.CLASS_STATELESS_CONTROLLER))
                .setResourceId(name)
                .setResourceName(name)
                .putProperties(cneeNamespace + CneeOntology.PROP_NAMESPACE, ns)
                .build();

        publisher.publish(SensorEventPublisher.withTimestamp(
                SensorEvent.newBuilder().setEntityDiscovered(discovered)));

        if (rs.getMetadata().getOwnerReferences() != null) {
            for (OwnerReference owner : rs.getMetadata().getOwnerReferences()) {
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

        log.debug("ReplicaSetSensor: added replicaset {}/{}", ns, name);
    }

    void onReplicaSetDeleted(ReplicaSet rs) {
        if (rs.getMetadata() == null) {
            return;
        }
        String ns = rs.getMetadata().getNamespace();
        String name = rs.getMetadata().getName();
        String iri = iriFactory.namespacedIri(CneeOntology.KIND_REPLICSET, ns, name);

        EntityDeletedEvent deleted = EntityDeletedEvent.newBuilder()
                .setResourceIri(iri)
                .setOntologyType(iriFactory.typeIri(CneeOntology.CLASS_STATELESS_CONTROLLER))
                .build();

        publisher.publish(SensorEventPublisher.withTimestamp(
                SensorEvent.newBuilder().setEntityDeleted(deleted)));

        log.debug("ReplicaSetSensor: deleted replicaset {}/{}", ns, name);
    }
}
