package com.kubiki.controller.sample.actions.infra;

import com.kubiki.controller.commons.actions.dtos.infra.ResizeVolumeActionArgs;
import com.kubiki.controller.commons.annotations.simple.Action;
import com.kubiki.controller.commons.definitons.ActionBase;
import com.kubiki.controller.commons.definitons.ActionState;

@Action(name = "ResizeVolumeAction", argClass = ResizeVolumeActionArgs.class)
public class ResizeVolumeAction extends ActionBase {
    public ResizeVolumeAction(ResizeVolumeActionArgs args, int retryNum, long idempotencyWindow) {
        super(null, "ResizeVolumeAction", args, ActionState.STARTED, retryNum, idempotencyWindow, null);
    }

}
