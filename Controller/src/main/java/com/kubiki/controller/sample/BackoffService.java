package com.kubiki.controller.sample;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import io.kubernetes.client.util.Config;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import java.io.IOException;

@Service
@Log4j2
public class BackoffService {
    private CoreV1Api api;


    public BackoffService() throws IOException {
        log.info("BackoffService constructor");

        ApiClient client = Config.defaultClient();
        Configuration.setDefaultApiClient(client);
        this.api = new CoreV1Api();
    }

    public void changePodCPUBackoff(String namespace, String podName, String newMemorySize) throws ApiException {
        log.info("changePodCPUBackoff: newMemorySize={}", newMemorySize);

        String currentMemorySize = "";
        while(!newMemorySize.equals(currentMemorySize)) {
            V1Pod pod = api.readNamespacedPod(podName, namespace, null);
            V1Container container = pod.getSpec().getContainers().get(0);
            V1ResourceRequirements resources = container.getResources();
            currentMemorySize = String.valueOf(resources.getRequests().get("cpu"));
            log.info("Current memory size={}", currentMemorySize);
        }
        log.info("Memory size successfully updated!");
    }
}
