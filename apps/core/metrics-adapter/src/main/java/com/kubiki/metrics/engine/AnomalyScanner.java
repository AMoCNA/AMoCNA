package com.kubiki.metrics.engine;

import com.kubiki.common.config.AmocnaCommonProperties;
import com.kubiki.common.model.GraphUpdateMessage;
import com.kubiki.metrics.graph.GraphWriter;
import com.kubiki.metrics.prometheus.PrometheusClient;
import com.kubiki.metrics.prometheus.ThresholdsLoader;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnomalyScanner {
    private final PrometheusClient prometheusClient;
    private final GraphWriter graphWriter;
    private final RabbitTemplate rabbitTemplate;
    private final ThresholdsLoader thresholdsLoader;
    private final AmocnaCommonProperties properties;

    private final MeterRegistry meterRegistry;

    private final Map<String, Integer> violationCounter = new ConcurrentHashMap<>();
    private final Map<String, java.time.Instant> lastTriggeredTimes = new ConcurrentHashMap<>();

    @Scheduled(fixedDelayString = "${scanner.interval:10000}")
    @Timed(value = "amocna.monitor.scan", description = "Time taken to perform anomaly scan")
    public void scan() {
        log.debug("Starting anomaly scan...");

        List<AnomalyBatchItem> batchItems = Flux.fromIterable(thresholdsLoader.getThresholds())
                .flatMap(threshold -> prometheusClient.query(threshold.query())
                        .onErrorResume(e -> {
                            log.error("Failed to query Prometheus for threshold: {}", threshold.name(), e);
                            return Flux.empty();
                        })
                        .flatMap(result -> {
                            String resourceName = result.labels().get(threshold.resourceLabel());
                            String namespace = threshold.namespaceLabel() != null
                                    ? result.labels().get(threshold.namespaceLabel())
                                    : null;

                            if (!StringUtils.hasText(resourceName)) {
                                log.warn("Result returned but resource label '{}' is missing", threshold.resourceLabel());
                                return Mono.empty();
                            }

                            String targetResourceIri = buildResourceIri(threshold.resourceKind(), namespace, resourceName);
                            String key = threshold.name() + ":" + targetResourceIri;

                            if (isViolated(result.value(), threshold.operator(), threshold.value())) {
                                int count = violationCounter.getOrDefault(key, 0) + 1;
                                log.warn("[BENCHMARK] Threshold breached at {} for {}: {} for resource {} (Value: {} {} {}). Persistence: {}/{}", System.currentTimeMillis(),
                                        threshold.name(), targetResourceIri, result.value(), threshold.operator(), threshold.value(), count, threshold.persistenceWindow());

                                if (count >= threshold.persistenceWindow()) {
                                    violationCounter.remove(key);

                                    // Cooldown Check
                                    java.time.Instant lastTriggered = lastTriggeredTimes.get(key);
                                    if (lastTriggered != null && java.time.Duration.between(lastTriggered, java.time.Instant.now()).toSeconds() < threshold.cooldownSeconds()) {
                                        log.info("Throttling anomaly trigger for {}: {} (cooldown active)", threshold.name(), targetResourceIri);
                                        return Mono.empty();
                                    }

                                    lastTriggeredTimes.put(key, java.time.Instant.now());
                                    return Mono.just(new AnomalyBatchItem(targetResourceIri, threshold.anomalyState(), true));
                                } else {
                                    violationCounter.put(key, count);
                                }
                            } else {
                                violationCounter.remove(key);
                                return Mono.just(new AnomalyBatchItem(targetResourceIri, threshold.anomalyState(), false));
                            }
                            return Mono.empty();
                        }))
                .collectList()
                .doOnError(e -> log.error("Unexpected error during anomaly scan", e))
                .onErrorResume(e -> Mono.just(List.of()))
                .block();

        if (batchItems != null && !batchItems.isEmpty()) {
            executeBatch(batchItems);
        }
    }

    private void executeBatch(List<AnomalyBatchItem> items) {
        log.debug("Executing anomaly batch with {} items", items.size());

        // Group by resource and anomaly state to avoid redundant operations in the same batch
        Map<String, AnomalyBatchItem> lastActionPerState = new HashMap<>();
        for (var item : items) {
            String key = item.resourceIri() + "#" + item.anomalyState();
            AnomalyBatchItem existing = lastActionPerState.get(key);
            if (existing == null || (!existing.isTrigger() && item.isTrigger())) {
                lastActionPerState.put(key, item);
            }
        }

        int triggers = 0;
        int clears = 0;
        for (var item : lastActionPerState.values()) {
            if (item.isTrigger()) {
                triggers++;
                triggerAnomaly(item.resourceIri(), item.anomalyState())
                        .subscribeOn(Schedulers.boundedElastic())
                        .subscribe();
            } else {
                clears++;
                clearAnomaly(item.resourceIri(), item.anomalyState())
                        .subscribeOn(Schedulers.boundedElastic())
                        .subscribe();
            }
        }

        if (triggers > 0 || clears > 0) {
            log.info("AnomalyScanner: Completed anomaly batch execution. Triggered {} anomalies, cleared {}.", triggers, clears);
        }
    }

    @Timed(value = "amocna.monitor.trigger", description = "Time taken to trigger anomaly in GraphDB")
    private Mono<Void> triggerAnomaly(String targetResourceIri, String anomalyState) {
        if (properties.ontology() == null || properties.ontology().resourcesNamespace() == null || anomalyState == null) {
            log.error("Cannot trigger anomaly: ontology properties or state is null");
            return Mono.empty();
        }
        String anomalyStateIri = properties.ontology().resourcesNamespace() + anomalyState;
        String correlationId = "metrics-" + UUID.randomUUID();
        return Mono.fromRunnable(() -> {
            MDC.put("correlationId", correlationId);
            MDC.put("resourceIri", targetResourceIri);
            MDC.put("changeKind", "STATE_CHANGED");
            try {
                log.info("Triggering anomaly {} for resource {}", anomalyStateIri, targetResourceIri);
                graphWriter.instantiateAnomaly(targetResourceIri, anomalyStateIri);

                // Notify via RabbitMQ
                GraphUpdateMessage message = new GraphUpdateMessage(
                        targetResourceIri,
                        anomalyStateIri,
                        "STATE_CHANGED",
                        correlationId
                );

                rabbitTemplate.convertAndSend("amocna.topic.exchange", "graph.updates.metrics-adapter", message);
                log.info("Successfully sent anomaly trigger notification for resource {}", targetResourceIri);
            } finally {
                MDC.clear();
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    @Timed(value = "amocna.monitor.clear", description = "Time taken to clear anomaly in GraphDB")
    private Mono<Void> clearAnomaly(String targetResourceIri, String anomalyState) {
        if (properties.ontology() == null || properties.ontology().resourcesNamespace() == null || anomalyState == null) {
            log.error("Cannot clear anomaly: ontology properties or state is null");
            return Mono.empty();
        }
        String anomalyStateIri = properties.ontology().resourcesNamespace() + anomalyState;
        String correlationId = "metrics-" + UUID.randomUUID();
        return Mono.fromRunnable(() -> {
            MDC.put("correlationId", correlationId);
            MDC.put("resourceIri", targetResourceIri);
            MDC.put("changeKind", "DELETED");
            try {
                log.info("Clearing anomaly {} for resource {}", anomalyStateIri, targetResourceIri);
                graphWriter.clearAnomalies(targetResourceIri, anomalyStateIri);

                // Notify via RabbitMQ
                GraphUpdateMessage message = new GraphUpdateMessage(
                        targetResourceIri,
                        anomalyStateIri,
                        "DELETED",
                        correlationId
                );

                rabbitTemplate.convertAndSend("amocna.topic.exchange", "graph.updates.metrics-adapter", message);
                log.info("Successfully sent anomaly clear notification for resource {}", targetResourceIri);
            } finally {
                MDC.clear();
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    private boolean isViolated(double actual, String operator, double threshold) {
        return switch (operator) {
            case ">" -> actual > threshold;
            case ">=" -> actual >= threshold;
            case "<" -> actual < threshold;
            case "<=" -> actual <= threshold;
            case "==" -> actual == threshold;
            default -> false;
        };
    }

    private String buildResourceIri(String kind, String namespace, String name) {
        if (StringUtils.hasText(namespace)) {
            return properties.ontology().resourcesNamespace() + kind + "_" + namespace + "_" + name;
        }
        return properties.ontology().resourcesNamespace() + kind + "_" + name;
    }

    private record AnomalyBatchItem(String resourceIri, String anomalyState, boolean isTrigger) {
    }
}
