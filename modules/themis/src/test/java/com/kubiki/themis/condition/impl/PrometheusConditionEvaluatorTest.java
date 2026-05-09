package com.kubiki.themis.condition.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kubiki.themis.config.ThemisProperties;
import com.kubiki.themis.exception.ConditionEvaluationException;
import com.kubiki.themis.model.ActionData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("PrometheusConditionEvaluator Unit Tests")
class PrometheusConditionEvaluatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ThemisProperties properties = new ThemisProperties(
            null,
            new ThemisProperties.Ontology("http://example.org/moa#"),
            new ThemisProperties.Prometheus("http://prometheus:9090")
    );
    private PrometheusConditionEvaluator evaluator;
    private MockRestServiceServer server;

    private static Stream<Arguments> provideErrorResponses() {
        return Stream.of(
                Arguments.of("", "Prometheus returned empty body"),
                Arguments.of("{\"status\": \"error\", \"error\": \"bad request\"}", "Prometheus query was not successful"),
                Arguments.of("{invalid:json}", "Error parsing Prometheus response")
        );
    }

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(properties.prometheus().url());
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        evaluator = new PrometheusConditionEvaluator(restClient, properties, objectMapper);
    }

    @Test
    @DisplayName("Should support PrometheusCondition type")
    void shouldSupportPrometheusCondition() {
        assertTrue(evaluator.supports("http://example.org/moa#PrometheusCondition"), "Should support PrometheusCondition type");
    }

    @Test
    @DisplayName("Should return true when result set is not empty")
    void shouldReturnTrueWhenResultIsNotEmpty() throws JsonProcessingException {
        String response = objectMapper.writeValueAsString(Map.of(
                "status", "success",
                "data", Map.of("result", java.util.List.of(Map.of("metric", Map.of(), "value", java.util.List.of(1, "1"))))
        ));

        server.expect(requestTo("http://prometheus:9090/api/v1/query?query=up"))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        ActionData.ConditionData condition = new ActionData.ConditionData("id", "type", "up");

        assertAll("Evaluator Validation",
                () -> assertTrue(evaluator.evaluate(condition), "Condition should be met"),
                () -> server.verify()
        );
    }

    @Test
    @DisplayName("Should return false when result set is empty")
    void shouldReturnFalseWhenResultIsEmpty() throws JsonProcessingException {
        String response = objectMapper.writeValueAsString(Map.of(
                "status", "success",
                "data", Map.of("result", java.util.List.of())
        ));

        server.expect(requestTo("http://prometheus:9090/api/v1/query?query=up"))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        ActionData.ConditionData condition = new ActionData.ConditionData("id", "type", "up");

        assertAll("Evaluator Validation",
                () -> assertFalse(evaluator.evaluate(condition), "Condition should NOT be met"),
                () -> server.verify()
        );
    }

    @ParameterizedTest(name = "Should throw exception on {1}")
    @MethodSource("provideErrorResponses")
    @DisplayName("Should handle various error response bodies")
    void shouldHandleErrorResponses(String responseBody, String expectedMessagePart) {
        server.expect(requestTo("http://prometheus:9090/api/v1/query?query=up"))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        ActionData.ConditionData condition = new ActionData.ConditionData("id", "type", "up");
        ConditionEvaluationException ex = assertThrows(ConditionEvaluationException.class, () -> evaluator.evaluate(condition));

        assertAll("Error Validation",
                () -> assertTrue(ex.getMessage().contains(expectedMessagePart), "Exception message should contain: " + expectedMessagePart),
                () -> server.verify()
        );
    }

    @Test
    @DisplayName("Should throw exception on network or 5xx error")
    void shouldThrowExceptionOnServerError() {
        server.expect(requestTo("http://prometheus:9090/api/v1/query?query=up"))
                .andRespond(withServerError());

        ActionData.ConditionData condition = new ActionData.ConditionData("id", "type", "up");
        ConditionEvaluationException ex = assertThrows(ConditionEvaluationException.class, () -> evaluator.evaluate(condition));

        assertAll("Error Validation",
                () -> assertTrue(ex.getMessage().contains("Prometheus query failed") || ex.getMessage().contains("Error evaluating Prometheus condition")),
                () -> server.verify()
        );
    }
}
