package com.kubiki.themis.execution;

import com.kubiki.common.model.ActionMessage;
import com.kubiki.common.model.Protocol;
import com.kubiki.themis.model.ExecutionResult;

/**
 * Generic interface for protocol-specific execution.
 */
public interface ProtocolExecutor {
    boolean supports(Protocol protocol);

    ExecutionResult executeStateless(ActionMessage action);
}
