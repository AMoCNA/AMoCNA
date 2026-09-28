package com.kubiki.palamedes.condition.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kubiki.common.config.AmocnaCommonProperties;
import com.kubiki.common.exception.ConditionEvaluationException;
import com.kubiki.palamedes.condition.ConditionStrategy;
import com.kubiki.palamedes.model.ActionData;
import org.eclipse.rdf4j.model.IRI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PrometheusConditionStrategy implements ConditionStrategy {
    private static final Logger log = LoggerFactory.getLogger(PrometheusConditionStrategy.class);

    private static final String CLASS_PROMETHEUS_CONDITION = "PrometheusCondition";
    private static final String PROMETHEUS_QUERY_PATH = "/api/v1/query";
    private static final String QUERY_PARAM = "query";
    private static final String RESPONSE_STATUS_SUCCESS = "success";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_DATA = "data";
    private static final String FIELD_RESULT = "result";

    private final RestClient restClient;
    private final AmocnaCommonProperties properties;
    private final ObjectMapper objectMapper;

    public PrometheusConditionStrategy(@Nullable @Qualifier("prometheusRestClient") RestClient restClient,
                                       AmocnaCommonProperties properties,
                                       ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(IRI conditionType) {
        String namespace = properties.ontology().actionsNamespace();
        return (namespace + CLASS_PROMETHEUS_CONDITION).equals(conditionType.stringValue());
    }

    @Override
    public boolean evaluate(ActionData.Condition condition) {
        if (restClient == null) {
            log.error("Prometheus RestClient is not configured");
            return false;
        }

        log.debug("Evaluating Prometheus condition: {}", condition.policy());

        try {
            String responseBody = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path(PROMETHEUS_QUERY_PATH)
                            .queryParam(QUERY_PARAM, "{query}")
                            .build(condition.policy()))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, resp) -> {
                        throw new ConditionEvaluationException("Prometheus API error: " + resp.getStatusCode());
                    })
                    .body(String.class);

            if (responseBody == null) {
                return false;
            }

            JsonNode response = objectMapper.readTree(responseBody);
            JsonNode statusNode = response.get(FIELD_STATUS);

            if (statusNode == null || !RESPONSE_STATUS_SUCCESS.equals(statusNode.asText())) {
                log.warn("Prometheus query was not successful: {}", responseBody);
                return false;
            }

            JsonNode dataNode = response.get(FIELD_DATA);
            if (dataNode != null) {
                JsonNode result = dataNode.get(FIELD_RESULT);
                // In this autonomic model, a non-empty result means the condition (alert) is active
                return result != null && result.isArray() && !result.isEmpty();
            }

            return false;
        } catch (Exception e) {
            log.error("Error evaluating Prometheus condition: {}", e.getMessage());
            return false;
        }
    }
}
