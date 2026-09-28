package com.kubiki.palamedes.scig;

/**
 * Ordered severity for SCIG policy matching (higher ordinal = more severe).
 */
public enum ScigSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    public boolean atLeast(ScigSeverity minimum) {
        return this.ordinal() >= minimum.ordinal();
    }

    public static ScigSeverity parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return LOW;
        }
        return ScigSeverity.valueOf(raw.trim().toUpperCase());
    }
}
