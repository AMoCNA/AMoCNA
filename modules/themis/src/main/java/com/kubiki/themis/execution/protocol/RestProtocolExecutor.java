package com.kubiki.themis.execution.protocol;

import com.kubiki.themis.execution.ProtocolExecutor;
import com.kubiki.themis.model.ActionData;
import com.kubiki.themis.model.Protocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
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
    public boolean supports(Protocol protocol) {
        return Protocol.REST.equals(protocol);
    }

    @Override
    public boolean execute(ActionData action, UUID executionId) {
        if (!(action instanceof ActionData.SimpleAction simpleAction)) {
            log.error("Action {} is not a SimpleAction", action.id());
            return false;
        }

        String url = hydrate(simpleAction.instruction(), simpleAction.data());
        HttpMethod method = simpleAction.method() != null ? simpleAction.method() : HttpMethod.GET;
        String payload = simpleAction.payload() != null ? hydrate(simpleAction.payload(), simpleAction.data()) : null;

        log.info("Executing {} request to {} for execution {}", method, url, executionId);

        try {
            RestClient.RequestBodySpec request = restClient.method(method).uri(url);

            if (payload != null && !payload.isEmpty()) {
                request.body(payload);
            }

            request.retrieve().toBodilessEntity();
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

