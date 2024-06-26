package com.kubiki.controller.sample.actions;

import com.kubiki.controller.commons.annotations.simple.Action;
import com.kubiki.controller.commons.definitons.ActionBase;
import com.kubiki.controller.sample.dto.ChangeApplicationThreadsNumberArgs;


@Action(name = "ChangeApplicationThreadsNumberAction", argClass = ChangeApplicationThreadsNumberArgs.class)
public class ChangeApplicationThreadsNumberAction extends ActionBase {
    public ChangeApplicationThreadsNumberAction(ChangeApplicationThreadsNumberArgs args) {
        super(args, 1, 10L);
    }
}