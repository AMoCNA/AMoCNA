package com.kubiki.metis.sensor.kubernetes;

import com.kubiki.metis.grpc.EntityDiscoveredEvent;
import com.kubiki.metis.grpc.RelationshipAssertedEvent;
import com.kubiki.metis.grpc.SensorEvent;
import com.kubiki.metis.knowledge.CneeOntology;
import com.kubiki.metis.sensor.IriFactory;
import com.kubiki.metis.sensor.SensorEventPublisher;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.LocalObjectReference;

import java.util.List;

/**
 * Emits Container, Image, ImageRegistry, Secret, and relationship events for
 * Kubernetes container specs.
 */
public final class ContainerImageEmitter {

    private final SensorEventPublisher publisher;
    private final IriFactory iriFactory;
    private final String cneeNamespace;

    public ContainerImageEmitter(SensorEventPublisher publisher,
                                 IriFactory iriFactory,
                                 String cneeNamespace) {
        this.publisher = publisher;
        this.iriFactory = iriFactory;
        this.cneeNamespace = cneeNamespace;
    }

    public void emitPodContainer(String namespace, String podName, Container container) {
        if (container == null || container.getName() == null) {
            return;
        }
        String podIri = iriFactory.namespacedIri(CneeOntology.KIND_POD, namespace, podName);
        String containerIri = iriFactory.containerIri(namespace, podName, container.getName());
        ImageReference ref = ImageReference.parse(container.getImage());
        emitContainerAndImage(containerIri, podIri, container.getName(), ref);
        emitPodPullsImageFrom(podIri, ref);
    }

    public void emitTemplateContainer(String namespace, String workloadKind, String workloadName, Container container) {
        if (container == null || container.getName() == null) {
            return;
        }
        String workloadIri = iriFactory.namespacedIri(workloadKind, namespace, workloadName);
        String containerIri = iriFactory.containerIri(namespace, workloadName, container.getName());
        ImageReference ref = ImageReference.parse(container.getImage());
        publishEntity(containerIri, CneeOntology.CLASS_CONTAINER, container.getName());
        emitImageOnly(ref);
        emitRelationship(workloadIri, CneeOntology.PROP_CONTAINS, containerIri);
        emitRelationship(containerIri, CneeOntology.PROP_USES_IMAGE, imageIriFor(ref));
    }

    private void emitContainerAndImage(String containerIri, String podIri, String containerName, ImageReference ref) {
        String imageIri = imageIriFor(ref);

        publishEntity(containerIri, CneeOntology.CLASS_CONTAINER, containerName);
        publishImageEntity(imageIri, ref);

        emitRelationship(podIri, CneeOntology.PROP_CONTAINS, containerIri);
        emitRelationship(containerIri, CneeOntology.PROP_USES_IMAGE, imageIri);
    }

    private void emitImageOnly(ImageReference ref) {
        publishImageEntity(imageIriFor(ref), ref);
    }

    /**
     * Emits a {@code cnee:Secret} entity and a {@code cnee:authenticatesWith}
     * relationship for each {@code imagePullSecrets} reference declared by an
     * ExecutionUnit (Pod) or Workload (Deployment).
     *
     * @param namespace   Kubernetes namespace the secret lives in
     * @param subjectIri  IRI of the referencing entity (Pod or Workload)
     * @param pullSecrets {@code spec.imagePullSecrets} (or template equivalent)
     */
    public void emitPodPullSecrets(String namespace, String podName, List<LocalObjectReference> pullSecrets) {
        String podIri = iriFactory.namespacedIri(CneeOntology.KIND_POD, namespace, podName);
        emitPullSecrets(namespace, podIri, pullSecrets);
    }

    public void emitWorkloadPullSecrets(String namespace, String workloadKind, String workloadName,
                                        List<LocalObjectReference> pullSecrets) {
        String workloadIri = iriFactory.namespacedIri(workloadKind, namespace, workloadName);
        emitPullSecrets(namespace, workloadIri, pullSecrets);
    }

    private void emitPullSecrets(String namespace, String subjectIri, List<LocalObjectReference> pullSecrets) {
        if (pullSecrets == null) {
            return;
        }
        for (LocalObjectReference ref : pullSecrets) {
            if (ref == null || ref.getName() == null) {
                continue;
            }
            String secretIri = iriFactory.secretIri(namespace, ref.getName());
            publishSecretEntity(secretIri, ref.getName(), namespace);
            emitRelationship(subjectIri, CneeOntology.PROP_AUTHENTICATES_WITH, secretIri);
        }
    }

    private void publishSecretEntity(String secretIri, String name, String namespace) {
        EntityDiscoveredEvent discovered = EntityDiscoveredEvent.newBuilder()
                .setResourceIri(secretIri)
                .setOntologyType(iriFactory.typeIri(CneeOntology.CLASS_SECRET))
                .setResourceId(name)
                .setResourceName(name)
                .putProperties(cneeNamespace + CneeOntology.PROP_NAMESPACE, namespace)
                .build();
        publisher.publish(SensorEventPublisher.withTimestamp(
                SensorEvent.newBuilder().setEntityDiscovered(discovered)));
    }

    private void emitPodPullsImageFrom(String podIri, ImageReference ref) {
        String registryIri = iriFactory.imageRegistryIri(ref.registryHost());
        publishEntity(registryIri, CneeOntology.CLASS_IMAGE_REGISTRY, ref.registryHost());
        emitRelationship(podIri, CneeOntology.PROP_PULLS_IMAGE_FROM, registryIri);
    }

    private String imageIriFor(ImageReference ref) {
        return iriFactory.imageIri(ref.repositoryPath(), ref.tag());
    }

    private void publishEntity(String iri, String typeLocal, String name) {
        EntityDiscoveredEvent discovered = EntityDiscoveredEvent.newBuilder()
                .setResourceIri(iri)
                .setOntologyType(iriFactory.typeIri(typeLocal))
                .setResourceId(name)
                .setResourceName(name)
                .build();
        publisher.publish(SensorEventPublisher.withTimestamp(
                SensorEvent.newBuilder().setEntityDiscovered(discovered)));
    }

    private void publishImageEntity(String imageIri, ImageReference ref) {
        EntityDiscoveredEvent discovered = EntityDiscoveredEvent.newBuilder()
                .setResourceIri(imageIri)
                .setOntologyType(iriFactory.typeIri(CneeOntology.CLASS_IMAGE))
                .setResourceId(ref.fullReference())
                .setResourceName(ref.repositoryPath())
                .putProperties(cneeNamespace + CneeOntology.PROP_VERSION, ref.tag())
                .build();
        publisher.publish(SensorEventPublisher.withTimestamp(
                SensorEvent.newBuilder().setEntityDiscovered(discovered)));
    }

    private void emitRelationship(String subjectIri, String predicateLocal, String objectIri) {
        RelationshipAssertedEvent rel = RelationshipAssertedEvent.newBuilder()
                .setSubjectIri(subjectIri)
                .setPredicate(cneeNamespace + predicateLocal)
                .setObjectIri(objectIri)
                .build();
        publisher.publish(SensorEventPublisher.withTimestamp(
                SensorEvent.newBuilder().setRelationshipAsserted(rel)));
    }
}
