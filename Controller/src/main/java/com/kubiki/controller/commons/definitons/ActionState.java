package com.kubiki.controller.commons.definitons;

public enum ActionState {
    STARTED,
    FINISHED, //success
    PENDING,
    FAILED, // failure
    ROLLED_BACK // after clean up

}
