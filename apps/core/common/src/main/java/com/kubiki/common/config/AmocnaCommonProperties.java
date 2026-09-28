package com.kubiki.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.Objects;

@ConfigurationProperties(prefix = AmocnaCommonProperties.PREFIX)
public record AmocnaCommonProperties(
    @NestedConfigurationProperty Ontology  ontology,
    @NestedConfigurationProperty GraphDB graphdb,
    @NestedConfigurationProperty Prometheus prometheus
) {

    final static String PREFIX = "amocna";
    public record Ontology(
            String actionsNamespace,
            String actionsPrefix,
            String resourcesNamespace,
            String resourcesPrefix,
            String bridgeNamespace,
            String bridgePrefix
    ) {
        public Ontology {
            Objects.requireNonNull(actionsNamespace, PREFIX + ".ontology.actionsNamespace must not be null");
            Objects.requireNonNull(actionsPrefix, PREFIX + ".ontology.actionsPrefix must not be null");
            Objects.requireNonNull(resourcesNamespace, PREFIX + ".ontology.resourcesNamespace must not be null");
            Objects.requireNonNull(resourcesPrefix, PREFIX + ".ontology.resourcesPrefix must not be null");
            Objects.requireNonNull(bridgeNamespace, PREFIX + ".ontology.bridgeNamespace must not be null");
            Objects.requireNonNull(bridgePrefix, PREFIX + ".ontology.bridgePrefix must not be null");
        }
    }

    public record GraphDB(String url, String repositoryId, int timeoutMs) {
        public GraphDB {
            Objects.requireNonNull(url, PREFIX + ".graphdb.url must not be null");
            Objects.requireNonNull(repositoryId, PREFIX + ".graphdb.repositoryId must not be null");
            PropertiesUtils.requiredPositive(timeoutMs, PREFIX + ".graphdb.timeoutMs");
        }
    }


    public record Prometheus(String url) {
    }
}
