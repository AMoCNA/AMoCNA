package com.kubiki.controller.sample.actions;

import com.kubiki.controller.commons.annotations.simple.Action;
import com.kubiki.controller.commons.actions.dtos.MockRetryActionArgs;
import com.kubiki.controller.commons.definitons.ActionBase;
import com.kubiki.controller.commons.definitons.ActionState;

@Action(name = "MockRetryAction", argClass = MockRetryActionArgs.class)
public class MockRetryAction extends ActionBase {
    public MockRetryAction(MockRetryActionArgs args, Integer retryNum) {
        super(null, "MockRetryAction", args, ActionState.STARTED, retryNum, null, null);
    }
}
