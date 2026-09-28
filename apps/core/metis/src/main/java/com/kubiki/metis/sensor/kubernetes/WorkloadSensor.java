package com.kubiki.metis.sensor.kubernetes;

import com.kubiki.metis.config.MetisProperties;
import com.kubiki.metis.grpc.EntityDeletedEvent;
import com.kubiki.metis.grpc.EntityDiscoveredEvent;
import com.kubiki.metis.grpc.SensorEvent;
import com.kubiki.metis.knowledge.CneeOntology;
import com.kubiki.metis.sensor.IriFactory;
import com.kubiki.metis.sensor.SensorEventPublisher;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Watches Deployments and StatefulSets to map Workload Controllers.
 */
@Component
@ConditionalOnProperty(name = "metis.sensor.enabled", havingValue = "true", matchIfMissing = false)
public class WorkloadSensor extends AbstractNamespacedSensor {

    private final SensorEventPublisher publisher;
    private final IriFactory iriFactory;
    private final ContainerImageEmitter imageEmitter;
    private final String cneeNamespace;

    public WorkloadSensor(KubernetesClient client,
                          MetisProperties properties,
                          SensorEventPublisher publisher,
                          IriFactory iriFactory) {
        super(client, properties);
        this.publisher = publisher;
        this.iriFactory = iriFactory;
        this.cneeNamespace = properties.ontology().cneeNamespace();
        this.imageEmitter = new ContainerImageEmitter(
                publisher, iriFactory, properties.ontology().cneeNamespace());
    }

    private static boolean templateImagesChanged(Deployment oldDep, Deployment newDep) {
        return !java.util.Objects.equals(
                containerImages(oldDep.getSpec() != null ? oldDep.getSpec().getTemplate() : null),
                containerImages(newDep.getSpec() != null ? newDep.getSpec().getTemplate() : null));
    }

    private static boolean templateImagesChanged(StatefulSet oldSs, StatefulSet newSs) {
        return !java.util.Objects.equals(
                containerImages(oldSs.getSpec() != null ? oldSs.getSpec().getTemplate() : null),
                containerImages(newSs.getSpec() != null ? newSs.getSpec().getTemplate() : null));
    }

    private static java.util.List<String> containerImages(io.fabric8.kubernetes.api.model.PodTemplateSpec template) {
        if (template == null || template.getSpec() == null || template.getSpec().getContainers() == null) {
            return java.util.List.of();
        }
        return template.getSpec().getContainers().stream()
                .map(c -> c.getName() + "=" + c.getImage())
                .toList();
    }

    @Override
    public String name() {
        return "WorkloadSensor";
    }

    @Override
    protected SharedIndexInformer<?> createInformer(KubernetesClient client, String namespace) {
        // This is a bit tricky since AbstractNamespacedSensor expects one informer per sensor.
        // We'll manage the lifecycle of both informers manually or override start/stop.
        // For simplicity here, I'll return the Deployment informer and start StatefulSet separately.

        var ssOp = namespace != null
                ? client.apps().statefulSets().inNamespace(namespace)
                : client.apps().statefulSets().inAnyNamespace();

        ssOp.inform(new ResourceEventHandler<>() {
            @Override
            public void onAdd(StatefulSet ss) {
                onStatefulSetAdded(ss);
            }

            @Override
            public void onUpdate(StatefulSet old, StatefulSet next) {
                onStatefulSetUpdated(old, next);
            }

            @Override
            public void onDelete(StatefulSet ss, boolean unknown) {
                onStatefulSetDeleted(ss);
            }
        });

        var depOp = namespace != null
                ? client.apps().deployments().inNamespace(namespace)
                : client.apps().deployments().inAnyNamespace();

        return depOp.inform(new ResourceEventHandler<>() {
            @Override
            public void onAdd(Deployment dep) {
                onDeploymentAdded(dep);
            }

            @Override
            public void onUpdate(Deployment old, Deployment next) {
                onDeploymentUpdated(old, next);
            }

            @Override
            public void onDelete(Deployment dep, boolean unknown) {
                onDeploymentDeleted(dep);
            }
        });
    }

    private void onDeploymentAdded(Deployment dep) {
        emitDiscovery(CneeOntology.KIND_DEPLOYMENT, CneeOntology.CLASS_STATELESS_CONTROLLER, dep.getMetadata().getNamespace(), dep.getMetadata().getName());
        emitDeploymentTemplateImages(dep);
    }

    void onDeploymentUpdated(Deployment oldDep, Deployment newDep) {
        if (templateImagesChanged(oldDep, newDep)) {
            emitDeploymentTemplateImages(newDep);
        }
    }

    private void onStatefulSetAdded(StatefulSet ss) {
        emitDiscovery(CneeOntology.KIND_STATEFULSET, CneeOntology.CLASS_STATEFUL_CONTROLLER, ss.getMetadata().getNamespace(), ss.getMetadata().getName());
        emitStatefulSetTemplateImages(ss);
    }

    void onStatefulSetUpdated(StatefulSet oldSs, StatefulSet newSs) {
        if (templateImagesChanged(oldSs, newSs)) {
            emitStatefulSetTemplateImages(newSs);
        }
    }

    private void emitDeploymentTemplateImages(Deployment dep) {
        if (dep.getSpec() == null || dep.getSpec().getTemplate() == null
                || dep.getSpec().getTemplate().getSpec() == null) {
            return;
        }
        String ns = dep.getMetadata().getNamespace();
        String name = dep.getMetadata().getName();
        var containers = dep.getSpec().getTemplate().getSpec().getContainers();
        if (containers == null) {
            return;
        }
        for (io.fabric8.kubernetes.api.model.Container container : containers) {
            imageEmitter.emitTemplateContainer(ns, CneeOntology.KIND_DEPLOYMENT, name, container);
        }
        imageEmitter.emitWorkloadPullSecrets(ns, CneeOntology.KIND_DEPLOYMENT, name,
                dep.getSpec().getTemplate().getSpec().getImagePullSecrets());
    }

    private void emitStatefulSetTemplateImages(StatefulSet ss) {
        if (ss.getSpec() == null || ss.getSpec().getTemplate() == null
                || ss.getSpec().getTemplate().getSpec() == null) {
            return;
        }
        String ns = ss.getMetadata().getNamespace();
        String name = ss.getMetadata().getName();
        var containers = ss.getSpec().getTemplate().getSpec().getContainers();
        if (containers == null) {
            return;
        }
        for (io.fabric8.kubernetes.api.model.Container container : containers) {
            imageEmitter.emitTemplateContainer(ns, CneeOntology.KIND_STATEFULSET, name, container);
        }
    }

    private void onDeploymentDeleted(Deployment dep) {
        emitDeletion(CneeOntology.KIND_DEPLOYMENT, CneeOntology.CLASS_STATELESS_CONTROLLER, dep.getMetadata().getNamespace(), dep.getMetadata().getName());
    }

    private void onStatefulSetDeleted(StatefulSet ss) {
        emitDeletion(CneeOntology.KIND_STATEFULSET, CneeOntology.CLASS_STATEFUL_CONTROLLER, ss.getMetadata().getNamespace(), ss.getMetadata().getName());
    }

    private void emitDiscovery(String kind, String typeLocal, String ns, String name) {
        String iri = iriFactory.namespacedIri(kind, ns, name);
        String type = iriFactory.typeIri(typeLocal);

        EntityDiscoveredEvent discovered = EntityDiscoveredEvent.newBuilder()
                .setResourceIri(iri)
                .setOntologyType(type)
                .setResourceId(name)
                .setResourceName(name)
                .putProperties(cneeNamespace + CneeOntology.PROP_NAMESPACE, ns)
                .build();

        publisher.publish(SensorEventPublisher.withTimestamp(
                SensorEvent.newBuilder().setEntityDiscovered(discovered)));
    }

    private void emitDeletion(String kind, String typeLocal, String ns, String name) {
        String iri = iriFactory.namespacedIri(kind, ns, name);
        EntityDeletedEvent deleted = EntityDeletedEvent.newBuilder()
                .setResourceIri(iri)
                .setOntologyType(iriFactory.typeIri(typeLocal))
                .build();

        publisher.publish(SensorEventPublisher.withTimestamp(
                SensorEvent.newBuilder().setEntityDeleted(deleted)));
    }
}
