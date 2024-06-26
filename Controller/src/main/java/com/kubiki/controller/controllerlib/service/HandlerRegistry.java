package com.kubiki.controller.controllerlib.service;

import com.kubiki.controller.commons.annotations.complex.ComplexActionHandler;
import com.kubiki.controller.commons.annotations.simple.ActionCleanUpHandler;
import com.kubiki.controller.commons.annotations.simple.ActionFailureHandler;
import com.kubiki.controller.commons.annotations.simple.ActionHandler;
import com.kubiki.controller.controllerlib.dto.ActionHandlerDto;
import com.kubiki.controller.controllerlib.utils.ApplicationContextProvider;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

@Component
@AllArgsConstructor
public class HandlerRegistry implements ApplicationListener<ContextRefreshedEvent> {

    private final Map<String, ActionHandlerDto> actionHandlers = new HashMap<>();
    private final Map<String, ActionHandlerDto> failureHandlers = new HashMap<>();
    private final Map<String, ActionHandlerDto> cleanUpHandlers = new HashMap<>();

    private final ApplicationContextProvider applicationContextProvider;

    private void updateRegistry() throws BeansException {
        for (String beanName : applicationContextProvider.getApplicationContext().getBeanDefinitionNames()) {
            Object obj = applicationContextProvider.getApplicationContext().getBean(beanName);
            for (Method method : obj.getClass().getDeclaredMethods()) {
                addMethodToActionHandlersIfAnnotated(beanName, method);
                addMethodToFailureHandlersIfAnnotated(beanName, method);
                addMethodToCleanUpHandlersIfAnnotated(beanName, method);
            }
        }
    }

    private void addMethodToActionHandlersIfAnnotated(String beanName, Method method) {
        if (method.isAnnotationPresent(ActionHandler.class)) {
            actionHandlers.put(
                    method.getAnnotation(ActionHandler.class).action(),
                    new ActionHandlerDto(beanName, method));
        } else if (method.isAnnotationPresent(ComplexActionHandler.class)){
            actionHandlers.put(
                    method.getAnnotation(ComplexActionHandler.class).action(),
                    new ActionHandlerDto(beanName, method));
        }
    }

    private void addMethodToFailureHandlersIfAnnotated(String beanName, Method method) {
        if (method.isAnnotationPresent(ActionFailureHandler.class)) {
            failureHandlers.put(
                    method.getAnnotation(ActionFailureHandler.class).action(),
                    new ActionHandlerDto(beanName, method));
        }
    }

    private void addMethodToCleanUpHandlersIfAnnotated(String beanName, Method method) {
        if (method.isAnnotationPresent(ActionCleanUpHandler.class)) {
            cleanUpHandlers.put(
                    method.getAnnotation(ActionCleanUpHandler.class).action(),
                    new ActionHandlerDto(beanName, method));
        }
    }

    public ActionHandlerDto getActionHandler(String actionName) {
        return actionHandlers.get(actionName);
    }

    public ActionHandlerDto getFailureHandler(String actionName) {
        return failureHandlers.get(actionName);
    }

    public ActionHandlerDto getActionCleanUpHandler(String actionName) {
        return cleanUpHandlers.get(actionName);
    }

    @Override
    public void onApplicationEvent(@NonNull ContextRefreshedEvent event) {
        updateRegistry();
    }

    @Override
    public boolean supportsAsyncExecution() {
        return ApplicationListener.super.supportsAsyncExecution();
    }
}
