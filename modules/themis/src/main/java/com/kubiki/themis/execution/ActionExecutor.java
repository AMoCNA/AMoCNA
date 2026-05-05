package com.kubiki.themis.execution;

public interface ActionExecutor {
    boolean execute(String targetId);
    boolean compensate(String targetId);
    String getActionType();
}
