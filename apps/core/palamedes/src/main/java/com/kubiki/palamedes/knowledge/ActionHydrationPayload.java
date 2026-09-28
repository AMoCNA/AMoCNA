package com.kubiki.palamedes.knowledge;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Serializes template hydration variables on an action individual (key=value pairs joined by {@code |}).
 */
final class ActionHydrationPayload {

    private ActionHydrationPayload() {
    }

    static String serialize(Map<String, String> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return "";
        }
        return parameters.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("|"));
    }

    static Map<String, String> deserialize(String payload) {
        if (payload == null || payload.isBlank()) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (String part : payload.split("\\|")) {
            int eq = part.indexOf('=');
            if (eq > 0) {
                result.put(part.substring(0, eq), part.substring(eq + 1));
            }
        }
        return result;
    }
}
