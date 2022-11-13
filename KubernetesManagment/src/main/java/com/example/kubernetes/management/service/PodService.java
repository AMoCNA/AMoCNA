package com.example.kubernetes.management.service;


import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class PodService {

    static Logger logger = LoggerFactory.getLogger(PodService.class);
    private KubernetesClient client;

    public PodService() {
        Config config = new ConfigBuilder().build();
        this.client = new DefaultKubernetesClient(config);
    }

    public ResponseEntity deletePod(String namespace, String podName) {
        logger.info(String.format("Namespace: %s, pod Name: %s", namespace, podName));
        try {
            logger.info("Client found pod: " + client.pods().inNamespace(namespace).withName(podName).get().getMetadata().getName());
            this.client.pods().inNamespace(namespace).withName(podName).delete();
            return new ResponseEntity<>("Pod was successfully deleted", HttpStatus.OK);
        } catch(NullPointerException e) {
            logger.error("Pod with given name don't exist");
            return new ResponseEntity(HttpStatus.BAD_REQUEST);
        }
    }

}
