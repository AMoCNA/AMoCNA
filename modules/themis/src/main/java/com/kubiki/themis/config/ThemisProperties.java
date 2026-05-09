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
        @NestedConfigurationProperty Ontology ontology,
        @NestedConfigurationProperty Prometheus prometheus
) {
    public record GraphDB(String url, String repositoryId, int timeoutMs) {
    }

    public record Ontology(String moaNamespace) {
    }

    public record Prometheus(String url) {
    }
}
