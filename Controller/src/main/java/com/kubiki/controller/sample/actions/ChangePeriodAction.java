package com.kubiki.controller.sample.actions;

import com.kubiki.controller.commons.annotations.simple.Action;
import com.kubiki.controller.commons.definitons.ActionBase;
import com.kubiki.controller.commons.definitons.ActionState;
import com.kubiki.controller.sample.dto.ChangePeriodActionArgs;

@Action(name = "ChangePeriodAction", argClass = ChangePeriodActionArgs.class)
public class ChangePeriodAction extends ActionBase {
    public ChangePeriodAction(ChangePeriodActionArgs args, int retryNum, long idempotencyWindow) {
        super(null, "ChangePeriodAction", args, ActionState.STARTED, retryNum, idempotencyWindow, null);
    }
}
