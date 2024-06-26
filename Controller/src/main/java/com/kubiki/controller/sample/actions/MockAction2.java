package com.kubiki.controller.sample.actions;

import com.kubiki.controller.commons.actions.dtos.MockActionArgs;
import com.kubiki.controller.commons.annotations.simple.Action;
import com.kubiki.controller.commons.definitons.ActionBase;

@Action(name = "MockAction2", argClass = MockActionArgs.class)
public class MockAction2 extends ActionBase {
    public MockAction2(MockActionArgs args) {
        super(args, 1, 200000L);
    }
}
