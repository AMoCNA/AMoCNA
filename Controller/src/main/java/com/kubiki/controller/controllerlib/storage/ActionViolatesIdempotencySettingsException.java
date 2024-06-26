package com.kubiki.controller.controllerlib.storage;

import com.kubiki.controller.controllerlib.data.entity.Action;
import lombok.Getter;
import lombok.experimental.StandardException;

@Getter
@StandardException
public class ActionViolatesIdempotencySettingsException extends Exception {

}
