package com.kubiki.themis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Root configuration class for Themis, following the "Configuration Properties at Scale" pattern.
 * Uses nested records for namespace-based discovery.
 */
@ConfigurationProperties(prefix = "themis")
public record ThemisProperties(
    @NestedConfigurationProperty GraphDB graphdb,
    @NestedConfigurationProperty Executors executors
) {
    public record GraphDB(String url, String repositoryId) {}
    
    public record Executors(
        @NestedConfigurationProperty Kubernetes kubernetes,
        @NestedConfigurationProperty Logging logging
    ) {
        public record Kubernetes(String managementUrl, int timeoutMs) {}
        public record Logging(String level) {}
    }
}
