package com.kubiki.palamedes.model;

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
        int expectedStatusCode
) {
}
