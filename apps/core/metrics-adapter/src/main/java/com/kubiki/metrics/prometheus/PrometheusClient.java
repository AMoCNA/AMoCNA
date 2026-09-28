package com.kubiki.metrics.prometheus;

import com.kubiki.common.config.AmocnaCommonProperties;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class PrometheusClient {
    private final WebClient webClient;

    public PrometheusClient(WebClient.Builder builder, AmocnaCommonProperties properties) {
        String url = properties.prometheus().url();
        log.info("Initializing PrometheusClient with URL: {}", url);
        this.webClient = builder.baseUrl(url).build();
    }

    public Mono<Double> queryScalar(String query) {
        log.debug("Executing Prometheus scalar query: {}", query);
        return webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/query")
                        .queryParam("query", "{query}")
                        .build(query))
                .retrieve()
                .bodyToMono(PrometheusResponse.class)
                .flatMap(response -> {
                    if (!"success".equals(response.getStatus())) {
                        log.warn("Prometheus query returned non-success status: {}", response.getStatus());
                        return Mono.empty();
                    }

                    if (response.getData() == null ||
                            response.getData().getResult() == null ||
                            response.getData().getResult().isEmpty()) {
                        log.debug("Prometheus query returned no results for query: {}", query);
                        return Mono.empty();
                    }

                    try {
                        List<Object> value = response.getData().getResult().get(0).getValue();
                        if (value != null && value.size() > 1) {
                            String valStr = value.get(1).toString();
                            return Mono.just(Double.parseDouble(valStr));
                        }
                    } catch (Exception e) {
                        log.error("Failed to parse Prometheus result value for query: {}", query, e);
                        return Mono.error(e);
                    }

                    return Mono.empty();
                })
                .doOnError(e -> log.error("Error communicating with Prometheus for query: {}", query, e));
    }

    /**
     * Executes an instant query against Prometheus and returns the results.
     *
     * @param query PromQL query string
     * @return flux of results, each containing metric labels and the numeric value
     */
    public Flux<QueryResult> query(String query) {
        log.debug("Executing Prometheus vector query: {}", query);
        return webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/query")
                        .queryParam("query", "{query}")
                        .build(query))
                .retrieve()
                .bodyToMono(PrometheusResponse.class)
                .flatMapMany(response -> {
                    if (!"success".equals(response.getStatus())) {
                        log.warn("Prometheus query returned non-success status: {}", response.getStatus());
                        return Flux.empty();
                    }

                    if (response.getData() == null || response.getData().getResult() == null) {
                        return Flux.empty();
                    }

                    return Flux.fromIterable(response.getData().getResult())
                            .map(result -> {
                                Map<String, String> labels = result.getMetric();
                                double value = 0.0;
                                try {
                                    if (result.getValue() != null && result.getValue().size() > 1) {
                                        value = Double.parseDouble(result.getValue().get(1).toString());
                                    }
                                } catch (Exception e) {
                                    log.error("Failed to parse value for result {}: {}", result, e.getMessage());
                                }
                                return new QueryResult(labels, value);
                            });
                })
                .doOnError(e -> log.error("Error communicating with Prometheus for query: {}", query, e));
    }

    /**
     * A single result from a Prometheus instant query.
     */
    public record QueryResult(Map<String, String> labels, double value) {
    }

    @Data
    public static class PrometheusResponse {
        private String status;
        private PrometheusData data;
    }

    @Data
    public static class PrometheusData {
        private String resultType;
        private List<PrometheusResult> result;
    }

    @Data
    public static class PrometheusResult {
        private java.util.Map<String, String> metric;
        private List<Object> value;
    }
}
