package com.kubiki.controller.sample;

import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.StorageV1Api;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.util.Config;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.util.Collections;

@Service
@Log4j2
public class InfraService {
    private CoreV1Api api;
    private final BackoffService backoffService;

    public InfraService(BackoffService backoffService) throws IOException {
        log.info("InfraService constructor");
        this.backoffService = backoffService;

        ApiClient client = Config.defaultClient();
        Configuration.setDefaultApiClient(client);
        this.api = new CoreV1Api();
    }

    /* VOLUME */

    public void resizeVolume(String namespace, String pvcName, String newSize) throws ApiException {
        Quantity q = Quantity.fromString(newSize);

        V1PersistentVolumeClaim pvc = api.readNamespacedPersistentVolumeClaim(pvcName, namespace, null);
        pvc.getSpec().getResources().getRequests().put("storage", q);

        api.replaceNamespacedPersistentVolumeClaim(pvcName, namespace, pvc, null, null, null, null);
        log.info("PVC resized successfully to " + newSize);
    }

    public void createPVandPVC(String namespace, String apiVersion, String persistentVolumeName, String persistentVolumeClaimName, String storageClassName, String pvSize, String pvcSize, String hostPath) throws ApiException {
        log.info("Start creating PV. Namespace: {}, apiVersion: {}, pvName: {}, pvSize: {}, storageClassName: {}, hostPath: {}", namespace, apiVersion, persistentVolumeName, pvSize, storageClassName, hostPath);

        V1PersistentVolume pv = createAndReturnPV(apiVersion, persistentVolumeName, pvSize, storageClassName, hostPath);

        log.info("Start creating PVC. pvcName: {}, pvcSize: {}", persistentVolumeClaimName, pvcSize);

        V1PersistentVolumeClaim pvc = createAndReturnPVC(apiVersion, persistentVolumeClaimName, pvcSize, storageClassName);

        api.createPersistentVolume(pv, null, null, null, null);
        api.createNamespacedPersistentVolumeClaim(namespace, pvc, null, null, null, null);

        log.info("PV and PVC created successfully!");
    }

    public void createStorageClass(String apiVersion, String metadataName) throws ApiException {
        log.info("Start creating Storage Class. apiVersion: {}, metadataName: {}", apiVersion, metadataName);

        StorageV1Api storageApi = new StorageV1Api();

        V1StorageClass storageClass = new V1StorageClass()
                .apiVersion(apiVersion)
                .kind("StorageClass")
                .metadata(new V1ObjectMeta().name(metadataName))
                .provisioner("kubernetes.io/no-provisioner");

        V1StorageClass createdStorageClass = storageApi.createStorageClass(storageClass, null, null, null, null);

        log.info("Storage class created successfully!");
    }

    public void createPV(String apiVersion, String persistentVolumeName, String pvSize, String storageClassName, String hostPath) throws ApiException {
        log.info("Start creating PV. ApiVersion: {}, PersistentVolumeName: {}, PvSize: {}, StorageClassName: {}, HostPath: {}", apiVersion, persistentVolumeName, pvSize, storageClassName, hostPath);

        V1PersistentVolume pv = createAndReturnPV(apiVersion, persistentVolumeName, pvSize, storageClassName, hostPath);
        api.createPersistentVolume(pv, null, null, null, null);

        log.info("Successfully created PV.");
    }

    public void createPVC(String namespace, String apiVersion, String persistentVolumeClaimName, String pvcSize, String storageClassName) throws ApiException {
        log.info("Start creating PVC. ApiVersion: {}, PersistentVolumeClaimName: {}, PvcSize: {}, StorageClassName: {}", apiVersion, persistentVolumeClaimName, pvcSize, storageClassName);

        V1PersistentVolumeClaim pvc = createAndReturnPVC(apiVersion, persistentVolumeClaimName, pvcSize, storageClassName);
        api.createNamespacedPersistentVolumeClaim(namespace, pvc, null, null, null, null);
        log.info("Successfully created PVC.");
    }

    /* CPU */

    public void changePodCPU(String namespace, String podName, String newMemorySize) throws ApiException {
        log.info("Start changing pod CPU. Namespace: {}, podName: {}, newMemorySize: {}", namespace, podName, newMemorySize);

        V1Pod pod = api.readNamespacedPod(podName, namespace, null);

        // fallback if only prefix is specified
        if (pod != null) {
            V1Container container = pod.getSpec().getContainers().get(0); // Załóżmy, że jest tylko jeden kontener w podzie
            V1ResourceRequirements resources = new V1ResourceRequirements()
                    .requests(Collections.singletonMap("cpu", new Quantity(newMemorySize))); // 500 milicpu (0.5 CPU)
            container.getResources().setRequests(resources.getRequests());

            // Zaktualizuj pod w klastrze Kubernetes
            V1Pod updatedPod = api.replaceNamespacedPod(podName, namespace, pod, null, null, null, null);
            log.info("CPU resources updated for pod: " + podName);

            backoffService.changePodCPUBackoff(namespace, podName, newMemorySize);

        } else {
            log.info("Pod not found: " + podName);
        }
    }

    /* MEMORY */

    public void changePodMemory(String namespace, String podName, String newMemorySize, String maxMemorySize) throws ApiException {
        log.info("Start changing pod memory. Namespace: {}, podName: {}, newMemorySize: {}, maxMemorySize: {}", namespace, podName, newMemorySize, maxMemorySize);

        V1Pod pod = api.readNamespacedPod(podName, namespace, null);

        if (pod != null) {
            V1Container container = pod.getSpec().getContainers().get(0); // Załóżmy, że jest tylko jeden kontener w podzie
            V1ResourceRequirements resources = new V1ResourceRequirements()
                    .requests(Collections.singletonMap("memory", new Quantity(newMemorySize)))
                    .limits(Collections.singletonMap("memory", new Quantity(maxMemorySize)));
            container.getResources().setRequests(resources.getRequests());
            container.getResources().setLimits(resources.getLimits());

            V1Pod updatedPod = api.replaceNamespacedPod(podName, namespace, pod, null, null, null, null);

            log.info("Memory resources updated for pod: " + podName);
        } else {
            log.info("Pod not found: " + podName);
        }
    }

    private V1PersistentVolume createAndReturnPV(String apiVersion, String persistentVolumeName, String pvSize, String storageClassName, String hostPath) {
        V1PersistentVolume pv = new V1PersistentVolume()
                .apiVersion(apiVersion)
                .kind("PersistentVolume")
                .metadata(new V1ObjectMeta().name(persistentVolumeName))
                .spec(new V1PersistentVolumeSpec()
                        .capacity(Collections.singletonMap("storage", new Quantity(pvSize)))
                        .accessModes(Collections.singletonList("ReadWriteOnce"))
                        .persistentVolumeReclaimPolicy("Retain")
                        .storageClassName(storageClassName)
                        .hostPath(new V1HostPathVolumeSource().path(hostPath)));
        return pv;
    }

    private V1PersistentVolumeClaim createAndReturnPVC(String apiVersion, String persistentVolumeClaimName, String pvcSize, String storageClassName) {
        V1PersistentVolumeClaim pvc = new V1PersistentVolumeClaim()
                .apiVersion(apiVersion)
                .kind("PersistentVolumeClaim")
                .metadata(new V1ObjectMeta().name(persistentVolumeClaimName))
                .spec(new V1PersistentVolumeClaimSpec()
                        .accessModes(Collections.singletonList("ReadWriteOnce"))
                        .resources(new V1ResourceRequirements()
                                .requests(Collections.singletonMap("storage", new Quantity(pvcSize))))
                        .storageClassName(storageClassName));
        return pvc;
    }

    /* DELETING */

    public void deleteStorageClass(String storageClassName) throws ApiException {
        log.info("Deleting StorageClass with name: {}" + storageClassName);

        StorageV1Api storageApi = new StorageV1Api();

        storageApi.deleteStorageClass(storageClassName, null, null, null, null, null, null);

        log.info("StorageClass deleted successfully: " + storageClassName);
    }
}
