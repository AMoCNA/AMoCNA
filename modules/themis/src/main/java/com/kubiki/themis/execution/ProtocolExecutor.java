package com.kubiki.themis.execution;

import com.kubiki.themis.model.ActionData;
import java.util.UUID;

/**
 * Generic interface for protocol-specific execution.
 */
public interface ProtocolExecutor {
    boolean supports(String protocol);
    boolean execute(ActionData action, UUID executionId);
}
