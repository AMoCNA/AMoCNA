package com.kubiki.metis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.List;

@ConfigurationProperties(prefix = "metis")
public record MetisProperties(
        @NestedConfigurationProperty GraphDB graphdb,
        @NestedConfigurationProperty Ontology ontology,
        @NestedConfigurationProperty Sensor sensor
) {
    public record GraphDB(String url, String repositoryId, int timeoutMs) {
    }

    public record Ontology(String cneeNamespace) {
    }

    /**
     * Configuration for the built-in Kubernetes sensor layer.
     *
     * @param enabled         whether to start the sensor layer on application startup
     * @param namespaces      list of Kubernetes namespaces to watch;
     *                        empty list means watch all namespaces
     * @param batchSize       maximum number of events per IngestBatch call
     * @param flushIntervalMs how long (ms) to buffer events before flushing a batch
     */
    public record Sensor(
            boolean enabled,
            List<String> namespaces,
            int batchSize,
            long flushIntervalMs
    ) {
        /**
         * Defaults used when the sensor block is absent from application.yml.
         */
        public Sensor {
            if (namespaces == null) namespaces = List.of();
            if (batchSize <= 0) batchSize = 50;
            if (flushIntervalMs <= 0) flushIntervalMs = 500;
        }
    }
}
