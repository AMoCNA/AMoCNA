package com.kubiki.themis.execution.protocol;

import com.kubiki.themis.constants.ProtocolConstants;
import com.kubiki.themis.execution.ProtocolExecutor;
import com.kubiki.themis.model.ActionData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

/**
 * Generic REST Protocol Interpreter.
 * Ingests URL templates from GraphDB and executes them.
 */
@Component
public class RestProtocolExecutor implements ProtocolExecutor {
    private static final Logger log = LoggerFactory.getLogger(RestProtocolExecutor.class);
    private final RestClient restClient;

    public RestProtocolExecutor(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    @Override
    public boolean supports(String protocol) {
        return ProtocolConstants.REST.equalsIgnoreCase(protocol);
    }

    @Override
    public boolean execute(ActionData action, UUID executionId) {
        if (!(action instanceof ActionData.SimpleAction simpleAction)) {
            log.error("Action {} is not a SimpleAction", action.id());
            return false;
        }

        String url = hydrate(simpleAction.instruction(), simpleAction.data());
        String method = simpleAction.method() != null ? simpleAction.method().toUpperCase() : "GET";
        String payload = simpleAction.payload() != null ? hydrate(simpleAction.payload(), simpleAction.data()) : null;

        log.info("Executing {} request to {} for execution {}", method, url, executionId);

        try {
            switch (method) {
                case "GET" -> restClient.get().uri(url).retrieve().toBodilessEntity();
                case "POST" -> restClient.post().uri(url).body(payload != null ? payload : "").retrieve().toBodilessEntity();
                case "PUT" -> restClient.put().uri(url).body(payload != null ? payload : "").retrieve().toBodilessEntity();
                case "DELETE" -> restClient.delete().uri(url).retrieve().toBodilessEntity();
                default -> throw new IllegalArgumentException("Unsupported HTTP method: " + method);
            };
            // For now, we assume any non-error response is success
            return true;
        } catch (Exception e) {
            log.error("Failed to execute REST action {}: {}", action.id(), e.getMessage());
            return false;
        }
    }

    private String hydrate(String template, java.util.Map<String, String> data) {
        if (template == null) return null;
        String result = template;
        for (var entry : data.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }
}

