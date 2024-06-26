package com.kubiki.controller.controllerlib.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.lang.reflect.Method;

@AllArgsConstructor
@Data
public class ActionHandlerDto {
    private String beanName;
    private Method method;
}
