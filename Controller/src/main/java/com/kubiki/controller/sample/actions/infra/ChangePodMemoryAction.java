package com.kubiki.controller.sample.actions.infra;

import com.kubiki.controller.commons.actions.dtos.infra.ChangePodMemoryActionArgs;
import com.kubiki.controller.commons.annotations.simple.Action;
import com.kubiki.controller.commons.definitons.ActionBase;
import com.kubiki.controller.commons.definitons.ActionState;

@Action(name = "ChangePodMemoryAction", argClass = ChangePodMemoryActionArgs.class)
public class ChangePodMemoryAction extends ActionBase {
    public ChangePodMemoryAction(ChangePodMemoryActionArgs args, int retryNum, long idempotencyWindow) {
        super(null, "ChangePodMemoryAction", args, ActionState.STARTED, retryNum, idempotencyWindow, null);
    }
}
