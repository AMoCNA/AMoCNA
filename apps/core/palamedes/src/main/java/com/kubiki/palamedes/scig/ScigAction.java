package com.kubiki.palamedes.scig;

/**
 * Remediation action selected by a SCIG YAML policy.
 */
public enum ScigAction {
    PATCH_IMAGE,
    DELETE_POD,
    FAIL_SAFE;

    public static ScigAction parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return FAIL_SAFE;
        }
        return switch (raw.trim().toLowerCase()) {
            case "patch_image", "patch-image", "image_update" -> PATCH_IMAGE;
            case "delete_pod", "delete-pod", "quarantine" -> DELETE_POD;
            case "fail_safe", "fail-safe", "observe", "none" -> FAIL_SAFE;
            default -> throw new IllegalArgumentException("Unknown SCIG action: " + raw);
        };
    }
}
