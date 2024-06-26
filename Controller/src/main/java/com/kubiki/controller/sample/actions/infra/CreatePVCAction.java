package com.kubiki.controller.sample.actions.infra;

import com.kubiki.controller.commons.actions.dtos.infra.CreatePVCActionArgs;
import com.kubiki.controller.commons.annotations.simple.Action;
import com.kubiki.controller.commons.definitons.ActionBase;
import com.kubiki.controller.commons.definitons.ActionState;

@Action(name = "CreatePVCAction", argClass = CreatePVCActionArgs.class)
public class CreatePVCAction extends ActionBase {
    public CreatePVCAction(CreatePVCActionArgs args, int retryNum, long idempotencyWindow) {
        super(null, "CreatePVCAction", args, ActionState.STARTED, retryNum, idempotencyWindow, null);
    }
}
