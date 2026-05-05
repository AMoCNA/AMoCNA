package com.kubiki.themis.execution;

import com.kubiki.themis.model.ActionData;
import java.util.UUID;

/**
 * Generic interface for protocol-specific execution.
 */
public interface ProtocolExecutor {
  boolean execute(ActionData.SimpleAction action, UUID executionId);

  boolean compensate(ActionData.SimpleAction action, UUID executionId);

  String getSupportedProtocol();
}
