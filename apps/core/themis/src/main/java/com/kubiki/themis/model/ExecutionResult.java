package com.kubiki.themis.model;

import com.kubiki.common.model.ExecutionStatus;

/**
 * Result of a protocol-specific execution.
 *
 * @param observedStatusCode The actual status code (HTTP) or exit code (Shell) received.
 * @param success            Whether the execution is considered successful based on expected criteria.
 * @param errorMessage       Optional error message if execution failed.
 * @param status             The granular execution status.
 */
public record ExecutionResult(
        int observedStatusCode,
        boolean success,
        String errorMessage,
        ExecutionStatus status
) {
    public static ExecutionResult success(int statusCode) {
        return new ExecutionResult(statusCode, true, null, ExecutionStatus.COMPLETED);
    }

    public static ExecutionResult failure(int statusCode, String errorMessage, ExecutionStatus status) {
        return new ExecutionResult(statusCode, false, errorMessage, status);
    }

    public static ExecutionResult failure(int statusCode, String errorMessage) {
        return new ExecutionResult(statusCode, false, errorMessage, ExecutionStatus.FAILED_INTERNAL);
    }
}
