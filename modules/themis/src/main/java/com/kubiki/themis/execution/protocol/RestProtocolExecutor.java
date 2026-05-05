package com.kubiki.themis.execution.protocol;

import com.kubiki.themis.execution.ProtocolExecutor;
import com.kubiki.themis.model.ActionData;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.util.UUID;

/**
 * Generic REST Protocol Interpreter.
 * Ingests URL templates from GraphDB and executes them.
 */
@Component
public class RestProtocolExecutor implements ProtocolExecutor {
    private final RestTemplate restTemplate;

    public RestProtocolExecutor(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder.build();
    }

@Override
    public boolean execute(ActionData.SimpleAction action, UUID executionId) {
        String url = hydrateTemplate(action.instruction(), action.data());
        
        try {
            // Future: Use OpenAPI/WebClient for more complex interactions
            restTemplate.getForObject(url, String.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean compensate(ActionData.SimpleAction action, UUID executionId) {
        // Implementation based on compensation metadata in GraphDB
        return true;
    }

    @Override
    public String getSupportedProtocol() {
        return "REST";
    }

    private String hydrateTemplate(String template, java.util.Map<String, String> data) {
        String result = template;
        for (var entry : data.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }
}

