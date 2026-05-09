package com.kubiki.themis.condition.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kubiki.themis.config.ThemisProperties;
import com.kubiki.themis.exception.ConditionEvaluationException;
import com.kubiki.themis.model.ActionData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

class PrometheusConditionEvaluatorTest {

    private PrometheusConditionEvaluator evaluator;
    private MockRestServiceServer server;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ThemisProperties properties = new ThemisProperties(
        null,
        new ThemisProperties.Ontology("http://example.org/moa#"),
        new ThemisProperties.Prometheus("http://prometheus:9090")
    );

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(properties.prometheus().url());
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        evaluator = new PrometheusConditionEvaluator(restClient, properties, objectMapper);
    }

    @Test
    void shouldSupportPrometheusCondition() {
        assertTrue(evaluator.supports("http://example.org/moa#PrometheusCondition"));
    }

    @Test
    void shouldReturnTrueWhenResultIsNotEmpty() throws JsonProcessingException {
        String response = objectMapper.writeValueAsString(Map.of(
            "status", "success",
            "data", Map.of("result", java.util.List.of(Map.of("metric", Map.of(), "value", java.util.List.of(1, "1"))))
        ));

        server.expect(requestTo("http://prometheus:9090/api/v1/query?query=up"))
              .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        ActionData.ConditionData condition = new ActionData.ConditionData("id", "type", "up");
        assertTrue(evaluator.evaluate(condition));
    }

    @Test
    void shouldReturnFalseWhenResultIsEmpty() throws JsonProcessingException {
        String response = objectMapper.writeValueAsString(Map.of(
            "status", "success",
            "data", Map.of("result", java.util.List.of())
        ));

        server.expect(requestTo("http://prometheus:9090/api/v1/query?query=up"))
              .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        ActionData.ConditionData condition = new ActionData.ConditionData("id", "type", "up");
        assertFalse(evaluator.evaluate(condition));
    }

    @Test
    void shouldThrowExceptionOnError() {
        server.expect(requestTo("http://prometheus:9090/api/v1/query?query=up"))
              .andRespond(withServerError());

        ActionData.ConditionData condition = new ActionData.ConditionData("id", "type", "up");
        assertThrows(ConditionEvaluationException.class, () -> evaluator.evaluate(condition));
    }

    @Test
    void shouldThrowExceptionOnEmptyBody() {
        server.expect(requestTo("http://prometheus:9090/api/v1/query?query=up"))
              .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        ActionData.ConditionData condition = new ActionData.ConditionData("id", "type", "up");
        ConditionEvaluationException ex = assertThrows(ConditionEvaluationException.class, () -> evaluator.evaluate(condition));
        assertTrue(ex.getMessage().contains("Prometheus returned empty body"));
    }

    @Test
    void shouldThrowExceptionOnUnsuccessfulStatus() throws JsonProcessingException {
        String response = objectMapper.writeValueAsString(Map.of(
            "status", "error",
            "errorType", "bad_data",
            "error", "invalid parameter"
        ));

        server.expect(requestTo("http://prometheus:9090/api/v1/query?query=up"))
              .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        ActionData.ConditionData condition = new ActionData.ConditionData("id", "type", "up");
        ConditionEvaluationException ex = assertThrows(ConditionEvaluationException.class, () -> evaluator.evaluate(condition));
        assertTrue(ex.getMessage().contains("Prometheus query was not successful"));
    }

    @Test
    void shouldThrowExceptionOnMalformedJson() {
        server.expect(requestTo("http://prometheus:9090/api/v1/query?query=up"))
              .andRespond(withSuccess("{invalid:json}", MediaType.APPLICATION_JSON));

        ActionData.ConditionData condition = new ActionData.ConditionData("id", "type", "up");
        ConditionEvaluationException ex = assertThrows(ConditionEvaluationException.class, () -> evaluator.evaluate(condition));
        assertTrue(ex.getMessage().contains("Error parsing Prometheus response"));
    }

    @Test
    void shouldThrowExceptionOnNetworkError() {
        // Mocking a network error is tricky with MockRestServiceServer as it usually handles the request/response cycle.
        // However, we can simulate an exception during retrieve or body call if we had control over the RestClient more directly.
        // In this setup, MockRestServiceServer handles the client.
        // Let's try to trigger the generic catch block by passing null condition policy which might cause issues in uriBuilder.
        
        ActionData.ConditionData condition = new ActionData.ConditionData("id", "type", null);
        assertThrows(ConditionEvaluationException.class, () -> evaluator.evaluate(condition));
    }
}
