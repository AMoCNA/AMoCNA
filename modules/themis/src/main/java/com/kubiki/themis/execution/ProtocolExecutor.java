package com.kubiki.themis.execution;

import com.kubiki.themis.model.ActionData;
import com.kubiki.themis.model.Protocol;

import java.util.UUID;

/**
 * Generic interface for protocol-specific execution.
 */
public interface ProtocolExecutor {
    boolean supports(Protocol protocol);
    boolean execute(ActionData action, UUID executionId);
}
