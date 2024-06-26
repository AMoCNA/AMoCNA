package com.kubiki.controller.sample.actions;

import com.kubiki.controller.commons.annotations.simple.Action;
import com.kubiki.controller.commons.definitons.ActionBase;
import com.kubiki.controller.sample.dto.ChangeDataSourceArgs;

@Action(name = "ChangeDataSource", argClass = ChangeDataSourceArgs.class)
public class ChangeDataSourceAction extends ActionBase {
    public ChangeDataSourceAction(ChangeDataSourceArgs args) {
        super(args, 1, 60L);
    }
}