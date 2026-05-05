package com.kubiki.themis.execution.impl;

import com.kubiki.themis.execution.ActionExecutor;
import com.kubiki.themis.config.ThemisProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;

@Component
public class DeletePodExecutor implements ActionExecutor {
    private final RestTemplate restTemplate;
    private final String managementUrl;

    public DeletePodExecutor(ThemisProperties properties, RestTemplateBuilder restTemplateBuilder) {
        this.managementUrl = properties.executors().kubernetes().managementUrl();
        this.restTemplate = restTemplateBuilder.build();
    }

    @Override
    public boolean execute(String targetId) {
        // targetId format: namespace/podName
        String[] parts = targetId.split("/");
        if (parts.length != 2) return false;

        String url = String.format("%s/kubernetes/management/pod/delete?namespace=%s&podName=%s",
                managementUrl, parts[0], parts[1]);

        try {
            restTemplate.getForObject(url, Object.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean compensate(String targetId) {
        return true;
    }

    @Override
    public String getActionType() {
        return "DeletePodAction";
    }
}
