package com.kubiki.common.model;

public enum ExecutionStatus {
    IN_PROGRESS,
    COMPLETED,
    FAILED_HTTP,
    FAILED_TIMEOUT,
    FAILED_AUTH,
    FAILED_INTERNAL
}
