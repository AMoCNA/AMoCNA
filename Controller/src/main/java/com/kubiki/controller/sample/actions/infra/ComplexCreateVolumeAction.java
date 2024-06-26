package com.kubiki.controller.sample.actions.infra;

import com.kubiki.controller.commons.actions.dtos.infra.ComplexCreateVolumeArgs;
import com.kubiki.controller.commons.annotations.complex.ComplexAction;
import com.kubiki.controller.commons.definitons.ActionBase;
import com.kubiki.controller.commons.definitons.ActionState;

@ComplexAction(name = "CreateVolumeAction", argClass = ComplexCreateVolumeArgs.class)
public class ComplexCreateVolumeAction extends ActionBase {
    public ComplexCreateVolumeAction(ComplexCreateVolumeArgs args, int retryNum, long idempotencyWindow) {
        super(null, "CreateVolumeAction", args, ActionState.STARTED, retryNum, idempotencyWindow, null);
    }
}
