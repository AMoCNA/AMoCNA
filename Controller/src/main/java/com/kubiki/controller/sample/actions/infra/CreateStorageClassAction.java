package com.kubiki.controller.sample.actions.infra;

import com.kubiki.controller.commons.actions.dtos.infra.CreateStorageClassActionArgs;
import com.kubiki.controller.commons.annotations.simple.Action;
import com.kubiki.controller.commons.definitons.ActionBase;
import com.kubiki.controller.commons.definitons.ActionState;

@Action(name = "CreateStorageClassAction", argClass = CreateStorageClassActionArgs.class)
public class CreateStorageClassAction extends ActionBase {
    public CreateStorageClassAction(CreateStorageClassActionArgs args, int retryNum, long idempotencyWindow) {
        super(null, "CreateStorageClassAction", args, ActionState.STARTED, retryNum, idempotencyWindow, null);
    }
}
