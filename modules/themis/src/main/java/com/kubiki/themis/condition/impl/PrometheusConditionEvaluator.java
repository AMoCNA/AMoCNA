package com.kubiki.themis.condition.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kubiki.themis.condition.ConditionEvaluator;
import com.kubiki.themis.config.ThemisProperties;
import com.kubiki.themis.constants.OntologyConstants;
import com.kubiki.themis.exception.ConditionEvaluationException;
import com.kubiki.themis.model.ActionData;
import org.eclipse.rdf4j.model.IRI;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PrometheusConditionEvaluator implements ConditionEvaluator {

    private final RestClient restClient;
    private final ThemisProperties properties;
    private final ObjectMapper objectMapper;

    public PrometheusConditionEvaluator(@Qualifier("prometheusRestClient") RestClient restClient,
                                        ThemisProperties properties,
                                        ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(IRI conditionType) {
        String prometheusCondition = properties.ontology().moamNamespace() + OntologyConstants.CLASS_PROMETHEUS_CONDITION;
        return prometheusCondition.equals(conditionType.stringValue());
    }

    @Override
    public boolean evaluate(ActionData.ConditionData condition) {
        try {
            String responseBody = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/api/v1/query")
                            .queryParam("query", condition.policy())
                            .build())
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, resp) -> {
                        throw new ConditionEvaluationException("Prometheus query failed with status: " + resp.getStatusCode());
                    })
                    .body(String.class);

            if (responseBody == null) {
                throw new ConditionEvaluationException("Prometheus returned empty body");
            }

            JsonNode response = objectMapper.readTree(responseBody);
            if (!"success".equals(response.get("status").asText())) {
                throw new ConditionEvaluationException("Prometheus query was not successful");
            }

            JsonNode result = response.get("data").get("result");
            return result.isArray() && !result.isEmpty();
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new ConditionEvaluationException("Error parsing Prometheus response: " + e.getMessage(), e);
        } catch (Exception e) {
            if (e instanceof ConditionEvaluationException) {
                throw e;
            }
            throw new ConditionEvaluationException("Error evaluating Prometheus condition: " + e.getMessage(), e);
        }
    }
}
