package com.kubiki.themis.condition.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kubiki.themis.config.ThemisProperties;
import com.kubiki.themis.exception.ConditionEvaluationException;
import com.kubiki.themis.model.ActionData;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

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
        assertTrue(evaluator.supports(SimpleValueFactory.getInstance().createIRI("http://example.org/moa#PrometheusCondition")));
    }

    @Test
    void shouldReturnTrueWhenResultIsNotEmpty() throws JsonProcessingException {
        String response = objectMapper.writeValueAsString(Map.of(
                "status", "success",
                "data", Map.of("result", java.util.List.of(Map.of("metric", Map.of(), "value", java.util.List.of(1, "1"))))
        ));

        server.expect(requestTo("http://prometheus:9090/api/v1/query?query=up"))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        ActionData.ConditionData condition = new ActionData.ConditionData(
                SimpleValueFactory.getInstance().createIRI("http://example.org/moa#cond1"),
                SimpleValueFactory.getInstance().createIRI("http://example.org/moa#PrometheusCondition"),
                "up");
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

        ActionData.ConditionData condition = new ActionData.ConditionData(
                SimpleValueFactory.getInstance().createIRI("http://example.org/moa#cond1"),
                SimpleValueFactory.getInstance().createIRI("http://example.org/moa#PrometheusCondition"),
                "up");
        assertFalse(evaluator.evaluate(condition));
    }

    @Test
    void shouldThrowExceptionOnError() {
        server.expect(requestTo("http://prometheus:9090/api/v1/query?query=up"))
                .andRespond(withServerError());

        ActionData.ConditionData condition = new ActionData.ConditionData(
                SimpleValueFactory.getInstance().createIRI("http://example.org/moa#cond1"),
                SimpleValueFactory.getInstance().createIRI("http://example.org/moa#PrometheusCondition"),
                "up");
        assertThrows(ConditionEvaluationException.class, () -> evaluator.evaluate(condition));
    }
}
