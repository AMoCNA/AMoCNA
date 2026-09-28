package com.kubiki.metis.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link MetisProperties.Sensor} compact-constructor defaults.
 */
class MetisPropertiesTest {

    @Test
    void sensor_nullNamespaces_defaultsToEmptyList() {
        MetisProperties.Sensor s = new MetisProperties.Sensor(true, null, 50, 500);
        assertThat(s.namespaces()).isEmpty();
    }

    @Test
    void sensor_emptyNamespaces_isPreserved() {
        MetisProperties.Sensor s = new MetisProperties.Sensor(true, List.of(), 50, 500);
        assertThat(s.namespaces()).isEmpty();
    }

    @Test
    void sensor_zeroBatchSize_defaultsTo50() {
        MetisProperties.Sensor s = new MetisProperties.Sensor(true, List.of(), 0, 500);
        assertThat(s.batchSize()).isEqualTo(50);
    }

    @Test
    void sensor_negativeBatchSize_defaultsTo50() {
        MetisProperties.Sensor s = new MetisProperties.Sensor(true, List.of(), -10, 500);
        assertThat(s.batchSize()).isEqualTo(50);
    }

    @Test
    void sensor_zeroFlushInterval_defaultsTo500() {
        MetisProperties.Sensor s = new MetisProperties.Sensor(true, List.of(), 50, 0);
        assertThat(s.flushIntervalMs()).isEqualTo(500);
    }

    @Test
    void sensor_negativeFlushInterval_defaultsTo500() {
        MetisProperties.Sensor s = new MetisProperties.Sensor(true, List.of(), 50, -1);
        assertThat(s.flushIntervalMs()).isEqualTo(500);
    }

    @Test
    void sensor_validValuesAreUsedAsIs() {
        MetisProperties.Sensor s = new MetisProperties.Sensor(
                true, List.of("default", "production"), 200, 1000);
        assertThat(s.namespaces()).containsExactly("default", "production");
        assertThat(s.batchSize()).isEqualTo(200);
        assertThat(s.flushIntervalMs()).isEqualTo(1000);
    }
}
