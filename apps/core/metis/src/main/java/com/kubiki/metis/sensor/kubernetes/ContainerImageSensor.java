package com.kubiki.metis.sensor.kubernetes;

import com.kubiki.metis.config.MetisProperties;
import com.kubiki.metis.sensor.IriFactory;
import com.kubiki.metis.sensor.SensorEventPublisher;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * Watches Kubernetes Pods and emits Container and Image entities with
 * {@code cnee:usesImage} and {@code cnee:contains} relationships.
 */
@Component
@ConditionalOnProperty(name = "metis.sensor.enabled", havingValue = "true", matchIfMissing = false)
public class ContainerImageSensor extends AbstractNamespacedSensor {

    private final ContainerImageEmitter emitter;

    public ContainerImageSensor(KubernetesClient client,
                                MetisProperties properties,
                                SensorEventPublisher publisher,
                                IriFactory iriFactory) {
        super(client, properties);
        this.emitter = new ContainerImageEmitter(
                publisher, iriFactory, properties.ontology().cneeNamespace());
    }

    private static boolean containerImagesChanged(Pod oldPod, Pod newPod) {
        if (oldPod.getSpec() == null || newPod.getSpec() == null) {
            return true;
        }
        List<Container> oldContainers = oldPod.getSpec().getContainers();
        List<Container> newContainers = newPod.getSpec().getContainers();
        if (oldContainers == null || newContainers == null) {
            return !Objects.equals(oldContainers, newContainers);
        }
        if (oldContainers.size() != newContainers.size()) {
            return true;
        }
        for (int i = 0; i < oldContainers.size(); i++) {
            Container oldC = oldContainers.get(i);
            Container newC = newContainers.get(i);
            if (!Objects.equals(oldC.getName(), newC.getName())
                    || !Objects.equals(oldC.getImage(), newC.getImage())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String name() {
        return "ContainerImageSensor";
    }

    @Override
    protected SharedIndexInformer<Pod> createInformer(KubernetesClient client, String namespace) {
        var podOp = namespace != null
                ? client.pods().inNamespace(namespace)
                : client.pods().inAnyNamespace();

        return podOp.inform(new ResourceEventHandler<>() {
            @Override
            public void onAdd(Pod pod) {
                processPod(pod);
            }

            @Override
            public void onUpdate(Pod oldPod, Pod newPod) {
                if (containerImagesChanged(oldPod, newPod)) {
                    processPod(newPod);
                }
            }

            @Override
            public void onDelete(Pod pod, boolean deletedFinalStateUnknown) {
                // Deletion handled by PodSensor
            }
        });
    }

    void processPod(Pod pod) {
        if (pod.getMetadata() == null || pod.getSpec() == null) {
            return;
        }
        String ns = pod.getMetadata().getNamespace();
        String name = pod.getMetadata().getName();
        List<Container> containers = pod.getSpec().getContainers();
        if (containers == null) {
            return;
        }
        for (Container container : containers) {
            emitter.emitPodContainer(ns, name, container);
        }
        emitter.emitPodPullSecrets(ns, name, pod.getSpec().getImagePullSecrets());
    }
}
