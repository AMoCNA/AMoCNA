package com.kubiki.controller.sample.actions.infra;

import com.kubiki.controller.commons.actions.dtos.infra.CreatePVandPVCActionArgs;
import com.kubiki.controller.commons.annotations.simple.Action;
import com.kubiki.controller.commons.definitons.ActionBase;
import com.kubiki.controller.commons.definitons.ActionState;

@Action(name = "CreatePVandPVCAction", argClass = CreatePVandPVCActionArgs.class)
public class CreatePVandPVCAction extends ActionBase {
    public CreatePVandPVCAction(CreatePVandPVCActionArgs args, int retryNum, long idempotencyWindow) {
        super(null, "CreatePVandPVCAction", args, ActionState.STARTED, retryNum, idempotencyWindow, null);
    }
}
