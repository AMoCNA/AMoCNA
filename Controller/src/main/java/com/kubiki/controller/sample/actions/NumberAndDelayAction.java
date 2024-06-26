package com.kubiki.controller.sample.actions;

import com.kubiki.controller.commons.actions.dtos.NumberAndDelayActionArgs;
import com.kubiki.controller.commons.annotations.simple.Action;
import com.kubiki.controller.commons.definitons.ActionBase;
import com.kubiki.controller.commons.definitons.ActionState;

@Action(name = "NumberAndDelayAction", argClass = NumberAndDelayActionArgs.class)
public class NumberAndDelayAction extends ActionBase {
    public NumberAndDelayAction(NumberAndDelayActionArgs args, long idempotencyWidow) {
        super(null, "NumberAndDelayAction", args, ActionState.STARTED, 0, idempotencyWidow, null);
    }
}
