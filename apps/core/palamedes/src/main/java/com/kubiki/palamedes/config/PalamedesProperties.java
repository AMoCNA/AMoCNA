package com.kubiki.palamedes.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@ConfigurationProperties(prefix = "palamedes")
public record PalamedesProperties(
        @NestedConfigurationProperty Engine engine,
        @NestedConfigurationProperty States states,
        @NestedConfigurationProperty Utilities utilities,
        @NestedConfigurationProperty Vulnerability vulnerability,
        @NestedConfigurationProperty Scheduler scheduler
) {

    public static final long DEFAULT_VULNERABILITY_SCAN_INTERVAL_MS = 30_000L;
    public static final boolean DEFAULT_VULNERABILITY_ENABLED = true;
    public static final String DEFAULT_VULNERABILITY_UPGRADE_POLICY = "PATCH";
    public static final String DEFAULT_VULNERABILITY_CATALOG_LOCATION = "classpath:vulnerabilities/demo-catalog.yaml";
    public static final double DEFAULT_INFRASTRUCTURE_CAPACITY = 2.0;
    public static final double DEFAULT_CONTAINERIZATION_CAPACITY = 1.5;
    public static final double DEFAULT_APPLICATION_CAPACITY = 1.0;
    public static final double DEFAULT_ALPHA = 0.5;
    public static final double DEFAULT_BETA = 0.5;
    public static final double DEFAULT_UTILIZATION = 0.5;
    public static final double DEFAULT_MIN_DYNAMIC_COST = 0.01;

    private final static List<String> AVAILABLE_STATE_KEYS = List.of(
            "initial",
            "planned",
            "validated",
            "in-progress",
            "succeeded",
            "failed",
            "compensating"
    );

    public PalamedesProperties {
        if (vulnerability == null) {
            vulnerability = new Vulnerability(DEFAULT_VULNERABILITY_ENABLED, DEFAULT_VULNERABILITY_SCAN_INTERVAL_MS, DEFAULT_VULNERABILITY_UPGRADE_POLICY, DEFAULT_VULNERABILITY_CATALOG_LOCATION);
        }
        if (scheduler == null) {
            scheduler = new Scheduler();
        }
    }

    public record States(Map<String, String> actionStates) {
        public States {
            Objects.requireNonNull(actionStates, "palamedes.states.actionStates must not be null");
            com.kubiki.common.config.PropertiesUtils.availableOptions(actionStates, AVAILABLE_STATE_KEYS);
        }
    }

    public record Engine(long fallbackPipelineRateMs, long fallbackAnomalyScanRateMs, int defaultIdempotencySeconds,
                         int batchSize) {
        public Engine {
            com.kubiki.common.config.PropertiesUtils.requiredPositive(fallbackPipelineRateMs, "palamedes.engine.fallbackPipelineRateMs");
            com.kubiki.common.config.PropertiesUtils.requiredPositive(fallbackAnomalyScanRateMs, "palamedes.engine.fallbackAnomalyScanRateMs");
            com.kubiki.common.config.PropertiesUtils.requiredPositive(defaultIdempotencySeconds, "palamedes.engine.defaultIdempotencySeconds");
            com.kubiki.common.config.PropertiesUtils.requiredPositive(batchSize, "palamedes.engine.batchSize");
        }
    }

    public record Utilities(String actionPrefix, String stepPrefix, String compensationPrefix,
                            int sizeOfGeneratedUuid) {
        public Utilities {
            Objects.requireNonNull(actionPrefix, "palamedes.utilities.actionPrefix must not be null");
            Objects.requireNonNull(stepPrefix, "palamedes.utilities.stepPrefix must not be null");
            Objects.requireNonNull(compensationPrefix, "palamedes.utilities.compensationPrefix must not be null");
            com.kubiki.common.config.PropertiesUtils.requiredPositive(sizeOfGeneratedUuid, "palamedes.utilities.sizeOfGeneratedUuid");
        }
    }

    public record Vulnerability(
            boolean enabled,
            long scanIntervalMs,
            String upgradePolicy,
            String catalogLocation
    ) {
        public Vulnerability {
            if (scanIntervalMs <= 0) {
                scanIntervalMs = DEFAULT_VULNERABILITY_SCAN_INTERVAL_MS;
            }
            if (upgradePolicy == null || upgradePolicy.isBlank()) {
                upgradePolicy = DEFAULT_VULNERABILITY_UPGRADE_POLICY;
            }
            if (catalogLocation == null || catalogLocation.isBlank()) {
                catalogLocation = DEFAULT_VULNERABILITY_CATALOG_LOCATION;
            }
        }
    }

    public record OpposingPair(String first, String second) {}

    public static final List<OpposingPair> DEFAULT_OPPOSING_INTENTS = List.of(
            new OpposingPair("ScaleUp", "ScaleDown"),
            new OpposingPair("ScalingUp", "ScalingDown"),
            new OpposingPair("Start", "Stop")
    );

    public record Scheduler(
            double infrastructureCapacity,
            double containerizationCapacity,
            double applicationCapacity,
            double alpha,
            double beta,
            double defaultUtilization,
            double minDynamicCost,
            List<OpposingPair> opposingIntents,
            Map<String, Double> layerCapacities
    ) {
        public Scheduler {
            if (infrastructureCapacity <= 0) {
                infrastructureCapacity = DEFAULT_INFRASTRUCTURE_CAPACITY;
            }
            if (containerizationCapacity <= 0) {
                containerizationCapacity = DEFAULT_CONTAINERIZATION_CAPACITY;
            }
            if (applicationCapacity <= 0) {
                applicationCapacity = DEFAULT_APPLICATION_CAPACITY;
            }
            if (alpha < 0) {
                alpha = DEFAULT_ALPHA;
            }
            if (beta < 0) {
                beta = DEFAULT_BETA;
            }
            if (defaultUtilization < 0) {
                defaultUtilization = DEFAULT_UTILIZATION;
            }
            if (minDynamicCost <= 0) {
                minDynamicCost = DEFAULT_MIN_DYNAMIC_COST;
            }
            if (opposingIntents == null) {
                opposingIntents = DEFAULT_OPPOSING_INTENTS;
            }
            if (layerCapacities == null) {
                layerCapacities = Map.of(
                        "Infrastructure", infrastructureCapacity,
                        "Containerization", containerizationCapacity,
                        "Application", applicationCapacity
                );
            }
        }

        public Scheduler() {
            this(DEFAULT_INFRASTRUCTURE_CAPACITY, DEFAULT_CONTAINERIZATION_CAPACITY, DEFAULT_APPLICATION_CAPACITY,
                 DEFAULT_ALPHA, DEFAULT_BETA, DEFAULT_UTILIZATION, DEFAULT_MIN_DYNAMIC_COST,
                 DEFAULT_OPPOSING_INTENTS, Map.of(
                         "Infrastructure", DEFAULT_INFRASTRUCTURE_CAPACITY,
                         "Containerization", DEFAULT_CONTAINERIZATION_CAPACITY,
                         "Application", DEFAULT_APPLICATION_CAPACITY
                 ));
        }
    }
}
