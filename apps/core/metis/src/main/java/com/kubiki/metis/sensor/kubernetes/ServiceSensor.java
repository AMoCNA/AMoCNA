package com.kubiki.metis.sensor.kubernetes;

import com.kubiki.metis.config.MetisProperties;
import com.kubiki.metis.grpc.EntityDeletedEvent;
import com.kubiki.metis.grpc.EntityDiscoveredEvent;
import com.kubiki.metis.grpc.SensorEvent;
import com.kubiki.metis.knowledge.CneeOntology;
import com.kubiki.metis.sensor.IriFactory;
import com.kubiki.metis.sensor.SensorEventPublisher;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Watches Kubernetes Services and emits:
 * <ul>
 *   <li>{@link EntityDiscoveredEvent} — on service add</li>
 *   <li>{@link EntityDeletedEvent} — on service delete</li>
 * </ul>
 *
 * <p>CNEEOnt type: {@code cnee:Service}
 */
@Component
@ConditionalOnProperty(name = "metis.sensor.enabled", havingValue = "true", matchIfMissing = false)
public class ServiceSensor extends AbstractNamespacedSensor {

    private static final String ONTOLOGY_TYPE_LOCAL = CneeOntology.CLASS_SERVICE;

    private final SensorEventPublisher publisher;
    private final IriFactory iriFactory;

    public ServiceSensor(KubernetesClient client,
                         MetisProperties properties,
                         SensorEventPublisher publisher,
                         IriFactory iriFactory) {
        super(client, properties);
        this.publisher = publisher;
        this.iriFactory = iriFactory;
    }

    @Override
    public String name() {
        return "ServiceSensor";
    }

    @Override
    protected SharedIndexInformer<Service> createInformer(KubernetesClient client, String namespace) {
        var svcOp = namespace != null
                ? client.services().inNamespace(namespace)
                : client.services().inAnyNamespace();

        return svcOp.inform(new ResourceEventHandler<>() {
            @Override
            public void onAdd(Service svc) {
                onServiceAdded(svc);
            }

            @Override
            public void onUpdate(Service oldSvc, Service newSvc) {
                // Services don't have meaningful state transitions — re-emit discovery
                // only if the name/namespace changed (practically never), otherwise no-op.
            }

            @Override
            public void onDelete(Service svc, boolean deletedFinalStateUnknown) {
                onServiceDeleted(svc);
            }
        });
    }

    // -------------------------------------------------------------------------

    private void onServiceAdded(Service svc) {
        String ns = svc.getMetadata().getNamespace();
        String name = svc.getMetadata().getName();
        String iri = iriFactory.namespacedIri(CneeOntology.KIND_SERVICE, ns, name);
        String type = iriFactory.typeIri(ONTOLOGY_TYPE_LOCAL);

        EntityDiscoveredEvent discovered = EntityDiscoveredEvent.newBuilder()
                .setResourceIri(iri)
                .setOntologyType(type)
                .setResourceId(name)
                .setResourceName(name)
                .build();

        publisher.publish(SensorEventPublisher.withTimestamp(
                SensorEvent.newBuilder().setEntityDiscovered(discovered)));

        log.debug("ServiceSensor: added service {}/{}", ns, name);
    }

    private void onServiceDeleted(Service svc) {
        String ns = svc.getMetadata().getNamespace();
        String name = svc.getMetadata().getName();
        String iri = iriFactory.namespacedIri(CneeOntology.KIND_SERVICE, ns, name);
        String type = iriFactory.typeIri(ONTOLOGY_TYPE_LOCAL);

        EntityDeletedEvent deleted = EntityDeletedEvent.newBuilder()
                .setResourceIri(iri)
                .setOntologyType(type)
                .build();

        publisher.publish(SensorEventPublisher.withTimestamp(
                SensorEvent.newBuilder().setEntityDeleted(deleted)));

        log.debug("ServiceSensor: deleted service {}/{}", ns, name);
    }
}
