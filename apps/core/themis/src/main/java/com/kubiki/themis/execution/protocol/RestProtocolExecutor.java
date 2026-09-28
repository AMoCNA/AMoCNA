package com.kubiki.themis.execution.protocol;

import com.kubiki.common.model.ActionMessage;
import com.kubiki.common.model.ExecutionStatus;
import com.kubiki.common.model.Protocol;
import com.kubiki.themis.config.ThemisProperties;
import com.kubiki.themis.execution.ProtocolExecutor;
import com.kubiki.themis.model.ExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Generic REST Protocol Interpreter.
 * Executes hydrated REST requests with granular feedback.
 */
@Component
public class RestProtocolExecutor implements ProtocolExecutor {
    private static final Logger log = LoggerFactory.getLogger(RestProtocolExecutor.class);
    private static final int DEFAULT_TIMEOUT_FALLBACK = 30;
    private static final int MS_PER_SECOND = 1000;
    private static final int HTTP_STATUS_UNAUTHORIZED = 401;
    private static final int HTTP_STATUS_FORBIDDEN = 403;
    private static final int HTTP_STATUS_GATEWAY_TIMEOUT = 504;
    private static final int HTTP_STATUS_INTERNAL_SERVER_ERROR = 500;

    private final RestClient.Builder restClientBuilder;
    private final ThemisProperties themisProperties;
    private final io.github.resilience4j.circuitbreaker.CircuitBreaker circuitBreaker;

    public RestProtocolExecutor(RestClient.Builder restClientBuilder, ThemisProperties themisProperties) {
        this.restClientBuilder = restClientBuilder;
        this.themisProperties = themisProperties;

        ThemisProperties.CircuitBreaker cbProps = null;
        if (themisProperties != null && themisProperties.execution() != null) {
            cbProps = themisProperties.execution().circuitBreaker();
        }
        if (cbProps == null) {
            cbProps = new ThemisProperties.CircuitBreaker(50.0f, 10, 2, 10);
        }

        io.github.resilience4j.circuitbreaker.CircuitBreakerConfig circuitBreakerConfig =
                io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
                .failureRateThreshold(cbProps.failureRateThreshold())
                .waitDurationInOpenState(java.time.Duration.ofSeconds(cbProps.waitDurationInOpenStateSeconds()))
                .permittedNumberOfCallsInHalfOpenState(cbProps.permittedNumberOfCallsInHalfOpenState())
                .slidingWindowSize(cbProps.slidingWindowSize())
                .recordExceptions(ResourceAccessException.class, HttpServerErrorException.class, java.io.IOException.class)
                .ignoreExceptions(HttpClientErrorException.class)
                .build();
        this.circuitBreaker = io.github.resilience4j.circuitbreaker.CircuitBreaker.of("restExecutor", circuitBreakerConfig);
    }

    @Override
    public boolean supports(Protocol protocol) {
        return Protocol.REST.equals(protocol);
    }

    @Override
    public ExecutionResult executeStateless(ActionMessage action) {
        return doExecute(action);
    }

    private ExecutionResult doExecute(ActionMessage action) {
        try {
            return circuitBreaker.executeCallable(() -> executeCall(action));
        } catch (io.github.resilience4j.circuitbreaker.CallNotPermittedException e) {
            log.error("REST action {} blocked: Circuit Breaker is OPEN", action.actionId());
            return ExecutionResult.failure(HTTP_STATUS_GATEWAY_TIMEOUT, "Circuit Breaker is OPEN", ExecutionStatus.FAILED_TIMEOUT);
        } catch (Exception e) {
            log.error("REST action {} failed during circuit breaker invocation: {}", action.actionId(), e.getMessage());
            if (e instanceof ResourceAccessException || e instanceof java.io.IOException) {
                return ExecutionResult.failure(HTTP_STATUS_GATEWAY_TIMEOUT, e.getMessage(), ExecutionStatus.FAILED_TIMEOUT);
            }
            if (e instanceof HttpServerErrorException hsee) {
                return ExecutionResult.failure(hsee.getStatusCode().value(), e.getMessage(), ExecutionStatus.FAILED_HTTP);
            }
            return ExecutionResult.failure(HTTP_STATUS_INTERNAL_SERVER_ERROR, e.getMessage(), ExecutionStatus.FAILED_INTERNAL);
        }
    }

    private ExecutionResult executeCall(ActionMessage action) throws Exception {
        HttpMethod httpMethod = action.method() != null ? HttpMethod.valueOf(action.method().toUpperCase()) : HttpMethod.GET;
        String url = action.instruction();
        String payload = action.payload();
        int expectedStatusCode = action.expectedStatusCode();

        int fallbackTimeout = (themisProperties != null && themisProperties.execution() != null)
                ? themisProperties.execution().defaultTimeoutSeconds()
                : DEFAULT_TIMEOUT_FALLBACK;
        int usedTimeout = action.timeoutSeconds() > 0 ? action.timeoutSeconds() : fallbackTimeout;

        log.info("Executing REST {} request to {} for action {} with timeout {}s", httpMethod, url, action.actionId(), usedTimeout);

        try {
            int timeoutMs = usedTimeout * MS_PER_SECOND;
            org.springframework.http.client.SimpleClientHttpRequestFactory requestFactory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(timeoutMs);
            requestFactory.setReadTimeout(timeoutMs);
            RestClient perRequestClient = restClientBuilder.requestFactory(requestFactory).build();

            RestClient.RequestBodySpec request = perRequestClient.method(httpMethod).uri(url);

            // Auth Injection
            if ("BearerToken".equalsIgnoreCase(action.authMechanism())) {
                String token = themisProperties.secret().bearerToken();
                if (token != null && !token.isEmpty()) {
                    request.header("Authorization", "Bearer " + token);
                } else {
                    log.warn("BearerToken requested but themis.secret.bearer-token is missing");
                }
            }

            if (payload != null && !payload.isEmpty()) {
                request.body(payload);
            }

            ResponseEntity<Void> response = request.retrieve().toBodilessEntity();
            int observed = response.getStatusCode().value();

            if (observed == expectedStatusCode) {
                return ExecutionResult.success(observed);
            } else {
                return ExecutionResult.failure(observed, "Unexpected status code: " + observed, ExecutionStatus.FAILED_HTTP);
            }

        } catch (HttpClientErrorException e) {
            int observed = e.getStatusCode().value();
            log.error("REST action {} failed with client error {}: {}", action.actionId(), observed, e.getMessage());
            ExecutionStatus status = (observed == HTTP_STATUS_UNAUTHORIZED || observed == HTTP_STATUS_FORBIDDEN) ? ExecutionStatus.FAILED_AUTH : ExecutionStatus.FAILED_HTTP;
            return ExecutionResult.failure(observed, e.getMessage(), status);
        } catch (HttpServerErrorException e) {
            int observed = e.getStatusCode().value();
            log.error("REST action {} failed with server error {}: {}", action.actionId(), observed, e.getMessage());
            throw e;
        } catch (ResourceAccessException e) {
            log.error("REST action {} failed with timeout or connection error: {}", action.actionId(), e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Failed to execute REST action {}: {}", action.actionId(), e.getMessage());
            throw e;
        }
    }
}
