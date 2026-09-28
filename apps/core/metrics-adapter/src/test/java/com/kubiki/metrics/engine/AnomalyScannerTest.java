package com.kubiki.metrics.engine;

import com.kubiki.common.config.AmocnaCommonProperties;
import com.kubiki.metrics.graph.GraphWriter;
import com.kubiki.metrics.prometheus.PrometheusClient;
import com.kubiki.metrics.prometheus.ThresholdDefinition;
import com.kubiki.metrics.prometheus.ThresholdsLoader;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnomalyScannerTest {

    @Mock
    private PrometheusClient prometheusClient;
    @Mock
    private GraphWriter graphWriter;
    @Mock
    private RabbitTemplate rabbitTemplate;
    @Mock
    private ThresholdsLoader thresholdsLoader;

    private AnomalyScanner anomalyScanner;
    private AmocnaCommonProperties properties;

    @BeforeEach
    void setUp() {
        properties = new AmocnaCommonProperties(
                new AmocnaCommonProperties.Ontology(
                        "http://actions/", "moam", "http://resources/", "cnee", "http://bridge/", "bridge"
                ),
                new AmocnaCommonProperties.GraphDB("http://localhost:7200", "amocna", 5000),
                new AmocnaCommonProperties.Prometheus("http://localhost:9090")
        );
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        anomalyScanner = new AnomalyScanner(prometheusClient, graphWriter, rabbitTemplate, thresholdsLoader, properties, meterRegistry);
    }

    @Test
    void scan_HandlesRabbitMqErrorAndContinues() {
        // Given
        ThresholdDefinition t1 = new ThresholdDefinition("t1", "query1", ">", 0.5, "Anomaly1", "node", "name", null, 1, 0);
        ThresholdDefinition t2 = new ThresholdDefinition("t2", "query2", ">", 0.5, "Anomaly2", "node", "name", null, 1, 0);
        when(thresholdsLoader.getThresholds()).thenReturn(List.of(t1, t2));

        when(prometheusClient.query("query1")).thenReturn(Flux.just(new PrometheusClient.QueryResult(Map.of("name", "host1"), 1.0)));
        when(prometheusClient.query("query2")).thenReturn(Flux.just(new PrometheusClient.QueryResult(Map.of("name", "host2"), 1.0)));

        // Fail for the first one during rabbit MQ send
        doThrow(new IllegalArgumentException("SimpleMessageConverter error")).when(rabbitTemplate)
                .convertAndSend(anyString(), anyString(), any(Object.class));

        // When
        anomalyScanner.scan();

        // Then
        verify(graphWriter, timeout(2000).times(2)).instantiateAnomaly(anyString(), anyString());
        verify(rabbitTemplate, timeout(2000).times(2)).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    void scan_HandlesPrometheusErrorAndContinues() {
        // Given
        ThresholdDefinition t1 = new ThresholdDefinition("t1", "query1", ">", 0.5, "Anomaly1", "node", "name", null, 1, 0);
        ThresholdDefinition t2 = new ThresholdDefinition("t2", "query2", ">", 0.5, "Anomaly2", "node", "name", null, 1, 0);
        when(thresholdsLoader.getThresholds()).thenReturn(List.of(t1, t2));

        when(prometheusClient.query("query1")).thenReturn(Flux.error(new RuntimeException("Prometheus down")));
        when(prometheusClient.query("query2")).thenReturn(Flux.just(new PrometheusClient.QueryResult(Map.of("name", "host2"), 1.0)));

        // When
        anomalyScanner.scan();

        // Then
        verify(graphWriter, timeout(2000).times(1)).instantiateAnomaly(contains("host2"), anyString());
        verify(rabbitTemplate, timeout(2000).times(1)).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    void scan_EnforcesCooldown() {
        // Given
        // Threshold with 60s cooldown
        ThresholdDefinition t1 = new ThresholdDefinition("t1", "query1", ">", 0.5, "Anomaly1", "node", "name", null, 1, 60);
        when(thresholdsLoader.getThresholds()).thenReturn(List.of(t1));

        when(prometheusClient.query("query1")).thenReturn(Flux.just(new PrometheusClient.QueryResult(Map.of("name", "host1"), 1.0)));

        // When - First trigger
        anomalyScanner.scan();

        // Then - Triggered once
        verify(graphWriter, timeout(2000).times(1)).instantiateAnomaly(anyString(), anyString());

        // When - Second trigger (within cooldown)
        anomalyScanner.scan();

        // Then - Still only triggered once
        verify(graphWriter, timeout(2000).times(1)).instantiateAnomaly(anyString(), anyString());
    }

    @Test
    void scan_ClearsSpecificAnomalyStateWhenNotViolated() {
        // Given
        ThresholdDefinition t1 = new ThresholdDefinition("t1", "query1", ">", 0.5, "Anomaly1", "node", "name", null, 1, 0);
        when(thresholdsLoader.getThresholds()).thenReturn(List.of(t1));

        // 0.1 is not > 0.5 (not violated)
        when(prometheusClient.query("query1")).thenReturn(Flux.just(new PrometheusClient.QueryResult(Map.of("name", "host1"), 0.1)));

        // When
        anomalyScanner.scan();

        // Then - Clears ONLY the specific anomaly type for the resource
        verify(graphWriter, timeout(2000).times(1)).clearAnomalies("http://resources/node_host1", "http://resources/Anomaly1");
        verify(rabbitTemplate, timeout(2000).times(1)).convertAndSend(anyString(), anyString(), any(Object.class));
    }
}
