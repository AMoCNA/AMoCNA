package com.kubiki.common.model;

public record ActionStatusUpdate(
        String actionId,
        ExecutionStatus status,
        String errorMessage,
        int observedStatusCode
) {
}
