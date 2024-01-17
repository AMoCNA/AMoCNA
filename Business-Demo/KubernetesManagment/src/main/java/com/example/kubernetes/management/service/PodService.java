package com.example.kubernetes.management.service;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.PodResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;


@Service
public class PodService {

    static Logger logger = LoggerFactory.getLogger(PodService.class);
    private KubernetesClient client;

    public PodService() {
        Config config = new ConfigBuilder().build();
        this.client = new DefaultKubernetesClient(config);
    }

    public HttpStatus deletePod(String namespace, String podName) {
        logger.info(String.format("Namespace: %s, Pod Name: %s", namespace, podName));
        PodResource podResource = this.client.pods().inNamespace(namespace).withName(podName);
        if(podResource.get() == null) {
            logger.error("Pod with given name don't exist in given namespace");
            return HttpStatus.BAD_REQUEST;
        }
        podResource.delete();
        return HttpStatus.OK;
    }

}
