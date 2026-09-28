package com.kubiki.common.model;

public record ActionMessage(
        String actionId,
        Protocol protocol,
        String instruction,
        String method,
        String payload,
        String authMechanism,
        int timeoutSeconds,
        boolean isIdempotent,
        int maxRetries,
        int expectedStatusCode,
        int priority
) {
    public ActionMessage(
            String actionId,
            Protocol protocol,
            String instruction,
            String method,
            String payload,
            String authMechanism,
            int timeoutSeconds,
            boolean isIdempotent,
            int maxRetries,
            int expectedStatusCode
    ) {
        this(actionId, protocol, instruction, method, payload, authMechanism,
                timeoutSeconds, isIdempotent, maxRetries, expectedStatusCode, 0);
    }
}

