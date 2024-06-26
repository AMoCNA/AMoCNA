package com.kubiki.controller.sample.actions;

import com.kubiki.controller.commons.annotations.complex.ComplexAction;
import com.kubiki.controller.commons.definitons.ActionBase;
import com.kubiki.controller.commons.definitons.ActionState;
import com.kubiki.controller.sample.dto.ChangeCPUAndPeriodActionArgs;

@ComplexAction(name = "ChangeCPUAndPeriodAction", argClass = ChangeCPUAndPeriodActionArgs.class)
public class ChangeCPUAndPeriodAction extends ActionBase {
    public ChangeCPUAndPeriodAction(ChangeCPUAndPeriodActionArgs args, int retryNum, long idempotencyWindow) {
        super(null, "ChangeCPUAndPeriodAction", args, ActionState.STARTED, retryNum, idempotencyWindow, null);
    }
}
