package com.example.droolsprototype.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;


@Service
public class KubernetesManagementService {

    Logger logger = LoggerFactory.getLogger(KubernetesManagementService.class);

    private final RestTemplate restTemplate;
    private final String DELETE_POD_URL = "/kubernetes/management/pod/delete";

    public static String KUBERNETES_MANAGEMENT_URL;

    public KubernetesManagementService(RestTemplateBuilder restTemplateBuilder, @Value("${kubernetes-management:}") String kubernetesManagementUrl) {
        KubernetesManagementService.KUBERNETES_MANAGEMENT_URL = kubernetesManagementUrl;
        this.restTemplate = restTemplateBuilder.build();
    }

    public void deletePod(String namespace, String podName) {
        String destinationUrl = createDestinationUrl(namespace, podName);
        logger.info(String.format("Executing rules function with parameters: namespace - %s, pod name - %s", namespace, podName));
        HttpStatus status = restTemplate.getForObject(destinationUrl, HttpStatus.class);
        if(status == HttpStatus.OK) {
            logger.info("Executing function executed");
        } else {
            logger.error("Execute function failed");
        }
    }

    private String createDestinationUrl(String namespace, String podName) {
        StringBuilder sb = new StringBuilder();
        String url = KUBERNETES_MANAGEMENT_URL + DELETE_POD_URL;
        sb.append(url)
                .append("?")
                .append("namespace=").append(namespace)
                .append("&")
                .append("podName=").append(podName);
        return sb.toString();
    }

}
