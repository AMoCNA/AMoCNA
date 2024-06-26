package com.kubiki.controller.sample.actions;

import com.kubiki.controller.commons.annotations.complex.ComplexAction;
import com.kubiki.controller.commons.definitons.ActionBase;
import com.kubiki.controller.sample.dto.ScaleHeavyFlowActionArgs;

@ComplexAction(name = "ScaleHeavyFlowAction", argClass = ScaleHeavyFlowActionArgs.class)
public class ScaleHeavyFlowAction extends ActionBase {
    public ScaleHeavyFlowAction(ScaleHeavyFlowActionArgs args) {
        super(args, 0, 600L);
    }
}