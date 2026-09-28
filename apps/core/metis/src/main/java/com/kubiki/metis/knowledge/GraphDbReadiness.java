package com.kubiki.metis.knowledge;

import com.kubiki.metis.config.MetisProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Blocks Metis sensor startup until GraphDB responds on its HTTP protocol endpoint.
 */
public class GraphDbReadiness {

    private static final Logger log = LoggerFactory.getLogger(GraphDbReadiness.class);

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
    private static final long DEFAULT_MAX_WAIT_MS = Duration.ofMinutes(5).toMillis();
    private static final long DEFAULT_INITIAL_DELAY_MS = 2_000;
    private static final long DEFAULT_MAX_DELAY_MS = 30_000;

    private final String protocolUrl;
    private final long maxWaitMs;
    private final long initialDelayMs;
    private final long maxDelayMs;

    public GraphDbReadiness(MetisProperties properties) {
        this(properties, DEFAULT_MAX_WAIT_MS, DEFAULT_INITIAL_DELAY_MS, DEFAULT_MAX_DELAY_MS);
    }

    private GraphDbReadiness(MetisProperties properties, long maxWaitMs, long initialDelayMs, long maxDelayMs) {
        String baseUrl = properties.graphdb().url();
        this.protocolUrl = baseUrl.endsWith("/")
                ? baseUrl + "protocol"
                : baseUrl + "/protocol";
        this.maxWaitMs = maxWaitMs;
        this.initialDelayMs = initialDelayMs;
        this.maxDelayMs = maxDelayMs;
    }

    static GraphDbReadiness forTest(
            MetisProperties properties, long maxWaitMs, long initialDelayMs, long maxDelayMs) {
        return new GraphDbReadiness(properties, maxWaitMs, initialDelayMs, maxDelayMs);
    }

    private static void sleep(long delayMs) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for GraphDB", e);
        }
    }

    /**
     * Polls GraphDB until {@code /protocol} returns HTTP 2xx or the wait budget is exhausted.
     *
     * @throws IllegalStateException if GraphDB does not become reachable in time
     */
    public void awaitReady() {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .build();

        long deadline = System.currentTimeMillis() + maxWaitMs;
        long delayMs = initialDelayMs;
        int attempt = 0;

        while (System.currentTimeMillis() < deadline) {
            attempt++;
            if (isReachable(client)) {
                log.info("GraphDB is reachable at {} [attempt={}]", protocolUrl, attempt);
                return;
            }
            log.info("Waiting for GraphDB at {} [attempt={}, nextRetryMs={}]",
                    protocolUrl, attempt, delayMs);
            sleep(delayMs);
            delayMs = Math.min(delayMs * 2, maxDelayMs);
        }

        throw new IllegalStateException(
                "GraphDB did not become reachable at " + protocolUrl
                        + " within " + Duration.ofMillis(maxWaitMs).toSeconds() + " seconds");
    }

    private boolean isReachable(HttpClient client) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(protocolUrl))
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception e) {
            log.debug("GraphDB not reachable yet: {}", e.getMessage());
            return false;
        }
    }
}
