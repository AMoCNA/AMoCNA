package com.kubiki.controller.sample.actions;

import com.kubiki.controller.commons.annotations.simple.Action;
import com.kubiki.controller.commons.definitons.ActionBase;


@Action(name = "FailingAction", argClass = Integer.class)
public class FailingAction extends ActionBase {
    public FailingAction(Integer args, int retryNum) {
        super(args, retryNum, 0L);
    }
}