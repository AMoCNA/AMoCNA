package com.kubiki.controller.sample.actions.infra;

import com.kubiki.controller.commons.actions.dtos.infra.CreatePVActionArgs;
import com.kubiki.controller.commons.annotations.simple.Action;
import com.kubiki.controller.commons.definitons.ActionBase;
import com.kubiki.controller.commons.definitons.ActionState;

@Action(name = "CreatePVAction", argClass = CreatePVActionArgs.class)
public class CreatePVAction extends ActionBase {
    public CreatePVAction(CreatePVActionArgs args, int retryNum, long idempotencyWindow) {
        super(null, "CreatePVAction", args, ActionState.STARTED, retryNum, idempotencyWindow, null);
    }
}
