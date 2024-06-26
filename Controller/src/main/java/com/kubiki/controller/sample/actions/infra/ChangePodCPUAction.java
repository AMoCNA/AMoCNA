package com.kubiki.controller.sample.actions.infra;

import com.kubiki.controller.commons.actions.dtos.infra.ChangePodCPUActionArgs;
import com.kubiki.controller.commons.annotations.simple.Action;
import com.kubiki.controller.commons.definitons.ActionBase;
import com.kubiki.controller.commons.definitons.ActionState;

@Action(name = "ChangePodCPUAction", argClass = ChangePodCPUActionArgs.class)
public class ChangePodCPUAction extends ActionBase {
    public ChangePodCPUAction(ChangePodCPUActionArgs args, int retryNum, long idempotencyWindow) {
        super(null, "ChangePodCPUAction", args, ActionState.STARTED, retryNum, idempotencyWindow, null);
    }
}