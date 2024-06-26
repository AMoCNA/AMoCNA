package com.kubiki.controller.sample.actions.infra;

import com.kubiki.controller.commons.annotations.complex.ComplexAction;
import com.kubiki.controller.commons.definitons.ActionBase;
import com.kubiki.controller.commons.definitons.ActionState;
import com.kubiki.controller.sample.dto.ComplexCleanUpActionArgs;

@ComplexAction(name = "ComplexCleanUpAction", argClass = ComplexCleanUpActionArgs.class)
public class ComplexCleanUpAction extends ActionBase {
    public ComplexCleanUpAction(ComplexCleanUpActionArgs args, int retryNum, long idempotencyWindow) {
        super(null, "ComplexCleanUpAction", args, ActionState.STARTED, retryNum, idempotencyWindow, null);
    }
}
