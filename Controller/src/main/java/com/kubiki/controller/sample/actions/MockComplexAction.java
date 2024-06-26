package com.kubiki.controller.sample.actions;

import com.kubiki.controller.commons.actions.dtos.ComplexActionArgs;
import com.kubiki.controller.commons.annotations.complex.ComplexAction;
import com.kubiki.controller.commons.definitons.ActionBase;

@ComplexAction(name = "ComplexAction", argClass = ComplexActionArgs.class)
public class MockComplexAction extends ActionBase {
    public MockComplexAction(ComplexActionArgs args) {
        super(args, 1, null);

    }
}
