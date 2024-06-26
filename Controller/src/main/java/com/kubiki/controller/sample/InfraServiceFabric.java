package com.kubiki.controller.sample;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.storage.StorageClass;
import io.fabric8.kubernetes.api.model.storage.StorageClassBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;

import java.util.Collections;
import java.util.Map;

@Service
@Log4j2
public class InfraServiceFabric {

    private KubernetesClient client;

    public InfraServiceFabric() {
        Config config = new ConfigBuilder().build();
        this.client = new DefaultKubernetesClient(config);
    }

    public void changePodCPU(String namespace, String podName, String newLimit, String newRequests) {
        log.info("Start changing pod CPU. Namespace: {}, podName: {}, newLimit: {}, newRequests: {}", namespace, podName, newLimit, newRequests);
        log.info("Fabric");

        Deployment deployment = client.apps().deployments().inNamespace(namespace).withName(podName).get();
        deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getResources().setLimits(Map.of("cpu", new Quantity(newLimit)));
        deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getResources().setRequests(Map.of("cpu", new Quantity(newRequests)));

        client.apps().deployments().inNamespace(namespace).createOrReplace(deployment);
        log.info("CPU limit updated for pod: " + podName);
    }

    public void resizePV(String pvName, String newSize) {
        log.info("Start resizing PV, pvName: {}, newSize: {}", pvName, newSize);
        log.info("Fabric");

        PersistentVolume pv = client.persistentVolumes().withName(pvName).get();
        if (pv != null) {
            PersistentVolumeSpec spec = pv.getSpec();
            spec.getCapacity().put("storage", new Quantity(newSize));

            client.persistentVolumes().createOrReplace(pv);
            System.out.println("Persistent Volume resized: " + pvName);
        } else {
            System.err.println("Persistent Volume not found: " + pvName);
        }
    }

    public void createPVandPVC(String namespace, String pvName, String pvcName, String storageClassName, String pvSize, String pvcSize, String hostPath) {
        log.info("Start creating PV. Namespace: {}, pvName: {}, pvSize: {}, storageClassName: {}, hostPath: {}", namespace, pvName, pvSize, storageClassName, hostPath);
        log.info("Fabric");

        PersistentVolume pv = new PersistentVolumeBuilder()
                .withNewMetadata().withName(pvName).endMetadata()
                .withNewSpec()
                .withCapacity(Collections.singletonMap("storage", new Quantity(pvSize)))
                .withAccessModes("ReadWriteOnce")
                .withStorageClassName(storageClassName)
                .withHostPath(new HostPathVolumeSourceBuilder().withPath(hostPath).build())
                .endSpec()
                .build();

        client.persistentVolumes().create(pv);
        log.info("Persistent Volume created: {}", pvName);

        PersistentVolumeClaim pvc = new PersistentVolumeClaimBuilder()
                .withNewMetadata().withName(pvcName).withNamespace(namespace).endMetadata()
                .withNewSpec()
                .withAccessModes("ReadWriteOnce")
                .withStorageClassName(storageClassName)
                .withResources(new ResourceRequirementsBuilder()
                        .addToRequests("storage", new Quantity(pvcSize))
                        .build())
                .endSpec()
                .build();

        client.persistentVolumeClaims().create(pvc);
        log.info("Persistent Volume Claim created: {}", pvcName);
    }

    // CLEAN UP ACTION

    public void createStorageClass(String storageClassName) {
        log.info("Start creating StorageClass with name: {}", storageClassName);
        StorageClass storageClass = new StorageClassBuilder()
                .withNewMetadata()
                .withName("storageClassName")
                .endMetadata()
                .withProvisioner("kubernetes.io/no-provisioner")
                .build();

        client.storage().storageClasses().createOrReplace(storageClass);

        log.info("StorageClass created successfully");
    }

    public void deleteStorageClass(String storageClassName) {
        log.info("Deleting StorageClass with name: {}", storageClassName);
        boolean deleted = client.storage().storageClasses().withName(storageClassName).delete();
        if (deleted) {
            log.info("StorageClass deleted successfully");
        } else {
            log.info("StorageClass not found");
        }
    }
}
