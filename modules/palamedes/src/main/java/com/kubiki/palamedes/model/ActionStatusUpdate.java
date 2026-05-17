package com.kubiki.palamedes.model;

public record ActionStatusUpdate(
        String actionId,
        ExecutionStatus status,
        String errorMessage,
        int observedStatusCode
) {
}
