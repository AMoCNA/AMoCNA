package com.kubiki.controller.controllerlib.dto;

import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.util.Map;

@Data
@RequiredArgsConstructor
public class ExecutionContext {
    private final boolean success;
    private final Map<String, Object> failureContext;

    public ExecutionContext(final boolean success) {
        this.success = success;
        this.failureContext = Map.of();
    }
}
