package com.kubiki.metrics.prometheus;

/**
 * A single threshold rule that maps a Prometheus query result to a CNEEOnt anomaly state.
 *
 * <p>Configured via {@code palamedes.thresholds[]} in application.yml.
 * Designed to be extensible — in the future, these can be loaded from a GUI or GraphDB.
 *
 * @param name              human-readable name for logging
 * @param query             PromQL query (should return vector results with resource labels)
 * @param operator          comparison operator: {@code >}, {@code <}, {@code >=}, {@code <=}, {@code ==}
 * @param value             threshold value to compare against
 * @param anomalyState      CNEEOnt state local name to set when threshold is crossed
 *                          (e.g. "ContainerCPUThrottledState")
 * @param resourceKind      Kubernetes kind for IRI construction (e.g. "Pod", "Node")
 * @param resourceLabel     Prometheus label containing the resource name
 * @param namespaceLabel    Prometheus label containing the namespace (null for cluster-scoped)
 * @param persistenceWindow Number of consecutive violations required to trigger the anomaly
 */
public record ThresholdDefinition(
        String name,
        String query,
        String operator,
        double value,
        String anomalyState,
        String resourceKind,
        String resourceLabel,
        String namespaceLabel,
        int persistenceWindow,
        int cooldownSeconds
) {
}
