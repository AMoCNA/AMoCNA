package com.kubiki.common.config;


import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class PropertiesUtils {
    private PropertiesUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static void requiredPositive(long value, String propertyName) {
        if (value <= 0) {
            throw new IllegalArgumentException("'%s' must be positive".formatted(propertyName));
        }
    }


    public static <T> void availableOptions(Map<T, T> map, List<T> available) {
        Objects.requireNonNull(map, "Map must not be null");
        Objects.requireNonNull(available, "Available options list must not be null");

        for (var key : map.keySet()) {
            if (!available.contains(key)) {
                throw new IllegalArgumentException("Unsupported option key: '%s'".formatted(key));
            }
        }
    }
}
