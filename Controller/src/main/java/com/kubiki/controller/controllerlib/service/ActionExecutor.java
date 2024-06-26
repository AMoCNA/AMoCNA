package com.kubiki.controller.controllerlib.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kubiki.controller.commons.definitons.ActionBase;
import com.kubiki.controller.commons.definitons.ActionState;
import com.kubiki.controller.controllerlib.args.ArgRegistry;
import com.kubiki.controller.controllerlib.dto.ExecutionContext;
import com.kubiki.controller.controllerlib.dto.ActionHandlerDto;
import com.kubiki.controller.controllerlib.storage.ActionDto;
import com.kubiki.controller.controllerlib.storage.ActionStorage;
import com.kubiki.controller.controllerlib.utils.ApplicationContextProvider;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Map;
import java.util.Objects;

@Component
@AllArgsConstructor
@Log4j2
public class ActionExecutor {

    private final ApplicationContextProvider applicationContextProvider;

    private final ActionStorage actionStorage;

    private final ArgRegistry argRegistry;

    private final HandlerRegistry handlerRegistry;
    
    public ExecutionContext performAction(ActionDto actionDto) {
        try {
            return performInterruptibleAction(actionDto);
        } catch (InterruptedException e) {
            actionDto.getActionBase().setState(ActionState.FAILED);
            actionStorage.updateActionFromDto(actionDto);
            return new ExecutionContext(false, Map.of("Exception", e));
        }
    }

    public ExecutionContext performInterruptibleAction(ActionDto actionDto) throws InterruptedException {
        ActionBase actionBase = actionDto.getActionBase();
        // this line is important
        Object arg = new ObjectMapper().convertValue(actionBase.getArgs(), argRegistry.getArgClass(actionBase.getName()));
        Date lastTryTime = new Date();
        ExecutionContext ctx = delegateActionToHandler(actionBase, arg);

        if (!ctx.isSuccess()) {
            delegateActionToFailureHandler(actionBase, arg, ctx);
        }

        if(actionBase.getRetryNum() != null) {
            int iterator = Math.toIntExact(actionBase.getRetryNum());
            while(!ctx.isSuccess() && iterator > 0) {
                lastTryTime = new Date();
                ctx = delegateActionToHandler(actionBase, arg);

                if(!ctx.isSuccess()) {
                    delegateActionToFailureHandler(actionBase, arg, ctx);
                }
                iterator -= 1;
            }
        }

        if (!ctx.isSuccess()) {
            delegateActionToCleanUpHandler(actionBase);
            actionDto.getActionBase().setState(ActionState.ROLLED_BACK);
        }
        actionStorage.updateActionFromDto(changeActionStateAndTimes(ctx.isSuccess(), lastTryTime, actionDto));
        return ctx;
    }

    public ExecutionContext delegateActionToHandler(ActionBase action, Object arg) throws InterruptedException {
        ActionHandlerDto handler = handlerRegistry.getActionHandler(action.getName());
        ExecutionContext executionContext;
        try {
            executionContext = (ExecutionContext) handler.getMethod().invoke(
                    applicationContextProvider.getApplicationContext().getBean(handler.getBeanName()),
                    arg);
        } catch (Exception e) {
            log.warn("Action handler has thrown an Exception", e.getCause());
            if (e.getCause() instanceof InterruptedException ) {
                throw new InterruptedException("Action timeout");
            }
            executionContext = new ExecutionContext(false, Map.of("Exception", e));
        }
        return executionContext;
    }

    public void delegateActionToCleanUpHandler(ActionBase action) throws InterruptedException {
        ActionHandlerDto cleanUpHandler = handlerRegistry.getActionCleanUpHandler(action.getName());
        Object arg = new ObjectMapper().convertValue(action.getArgs(), argRegistry.getArgClass(action.getName()));
        if(Objects.nonNull(cleanUpHandler)) {
            try {
                cleanUpHandler.getMethod().invoke(
                        applicationContextProvider.getApplicationContext().getBean(cleanUpHandler.getBeanName()),
                        arg);
            } catch (Exception e) {
                log.warn("Action Cleanup handler has thrown an Exception: {}", e.getMessage());
                if (e.getCause() instanceof InterruptedException ) {
                    throw new InterruptedException("Action timeout");
                }
            }
        }
    }

    private void delegateActionToFailureHandler(ActionBase action, Object arg, ExecutionContext ctx) throws InterruptedException {
        ActionHandlerDto failureHandler = handlerRegistry.getFailureHandler(action.getName());
        if (Objects.nonNull(failureHandler)) {
            try {
                failureHandler.getMethod().invoke(
                        applicationContextProvider.getApplicationContext().getBean(failureHandler.getBeanName()),
                        arg,
                        ctx);
            } catch (Exception e) {
                log.warn("Action Failure handler has thrown an Exception: {}", e.getMessage());
                if (e.getCause() instanceof InterruptedException ) {
                    throw new InterruptedException("Action timeout");
                }
            }
        }
    }

    private ActionDto changeActionStateAndTimes(boolean isSuccess, Date lastTryTime, ActionDto actionDto) {
        ActionBase actionBase = actionDto.getActionBase();

        if(actionBase.getState() != ActionState.ROLLED_BACK) {
            if(isSuccess) {
                actionBase.setState(ActionState.FINISHED);
                actionDto.setSuccessPerformTime(lastTryTime);
            } else {
                actionBase.setState(ActionState.FAILED);
            }
        }

        actionDto.setLastTryTime(lastTryTime);
        return actionDto;
    }
}
