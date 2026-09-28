package com.kubiki.themis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties(prefix = "themis")
public record ThemisProperties(
        @NestedConfigurationProperty Secret secret,
        @NestedConfigurationProperty Execution execution
) {
    public static final int DEFAULT_TIMEOUT_SECONDS = 30;
    public static final String DEFAULT_SHELL_PATH = "/bin/sh";

    public record Secret(String bearerToken) {
    }

    public record Execution(
            int postConditionDelayMs,
            int defaultTimeoutSeconds,
            String defaultShellPath,
            @NestedConfigurationProperty CircuitBreaker circuitBreaker
    ) {
        public Execution {
            if (defaultTimeoutSeconds <= 0) {
                defaultTimeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
            }
            if (defaultShellPath == null || defaultShellPath.isBlank()) {
                defaultShellPath = DEFAULT_SHELL_PATH;
            }
            if (circuitBreaker == null) {
                circuitBreaker = new CircuitBreaker(50.0f, 10, 2, 10);
            }
        }

        public Execution(int postConditionDelayMs) {
            this(postConditionDelayMs, DEFAULT_TIMEOUT_SECONDS, DEFAULT_SHELL_PATH, new CircuitBreaker(50.0f, 10, 2, 10));
        }
    }

    public record CircuitBreaker(
            float failureRateThreshold,
            int waitDurationInOpenStateSeconds,
            int permittedNumberOfCallsInHalfOpenState,
            int slidingWindowSize
    ) {
        public CircuitBreaker {
            if (failureRateThreshold <= 0 || failureRateThreshold > 100) {
                failureRateThreshold = 50.0f;
            }
            if (waitDurationInOpenStateSeconds <= 0) {
                waitDurationInOpenStateSeconds = 10;
            }
            if (permittedNumberOfCallsInHalfOpenState <= 0) {
                permittedNumberOfCallsInHalfOpenState = 2;
            }
            if (slidingWindowSize <= 0) {
                slidingWindowSize = 10;
            }
        }
    }
}
