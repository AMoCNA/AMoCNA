package com.kubiki.controller.controllerlib.service.complex;

import com.kubiki.controller.commons.annotations.complex.ComplexAction;
import com.kubiki.controller.commons.annotations.simple.Action;
import com.kubiki.controller.commons.definitons.ActionBase;
import com.kubiki.controller.commons.definitons.ActionScheduleRequest;
import com.kubiki.controller.commons.definitons.ActionState;
import com.kubiki.controller.controllerlib.data.utils.ActionDtoUtils;
import com.kubiki.controller.controllerlib.service.ActionExecutor;
import com.kubiki.controller.controllerlib.service.ActionService;
import com.kubiki.controller.controllerlib.storage.ActionDto;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@AllArgsConstructor
@Log4j2
public class ComplexActionManager {

    private final static int POOLING_INTERVAL = 10000;
    private final ActionExecutor actionExecutor;
    private final ActionService actionService;

    public void performBatchActions(ActionScheduleRequest... requests) {
        Arrays.stream(requests).forEach(this::setActionName);
        List<ActionDto> performedActions = new ArrayList<>();
        boolean actionFailed = false;
        for(int i = 0; i < requests.length && !actionFailed; i++) {
            ActionBase action = requests[i].getAction();
            ActionDto actionDto = new ActionDto(action, 1, requests[i].getIdempotencyKey(), requests[i].getPriority(), null, null);
            actionDto = performOrShareAction(actionDto);
            if(actionDto.getActionBase().getState() == ActionState.FINISHED) {
                performedActions.add(actionDto);
            } else {
                actionFailed = true;
            }
        }

        if (actionFailed) {
            cleanUpSuccessActions(performedActions);
        } else {
            log.info("Complex action perform successfully!");
        }
    }

    private ActionDto performAction(ActionDto action) {
        try {
            actionExecutor.performAction(action);
        } catch (Exception e) {
            action.setActionBase(action.getActionBase().withState(ActionState.FAILED));
        }
        return action;
    }

    private void cleanUpSuccessActions(List<ActionDto> actionsToCleanUp) {
        log.info("Number of actions to clean up: {}.", actionsToCleanUp.size());
        Collections.reverse(actionsToCleanUp);
        for(ActionDto actionDto: actionsToCleanUp) {
            ActionBase actionBase = actionDto.getActionBase();
            Long actionId = actionBase.getId();
            Integer sharingCounter = actionService.decrementSharingCounter(actionId, 1);
            if (sharingCounter == 0) {
                try {
                    actionExecutor.delegateActionToCleanUpHandler(actionBase);
                } catch (InterruptedException ignored) {}
                actionDto.getActionBase().setState(ActionState.ROLLED_BACK);
                actionService.updateAction(actionDto);
            } else {
                log.info("Sharing counter is >= 0");
            }
        }
        log.info("Cleaned actions successfully!");
    }

    private ActionDto waitForSharedActionToFinish(ActionDto actionDto) {
        while (actionDto.getActionBase().getState() == ActionState.STARTED) {
            try {
                Thread.sleep(POOLING_INTERVAL);
            } catch (Exception ignored) {}
            actionDto = actionService.refreshAction(actionDto);
        }
        return actionDto;
    }

    // todo transaction ???
    private ActionDto performOrShareAction(ActionDto actionDto) {
        Optional<ActionDto> idempotentAction = actionService.getIdempotentAction(actionDto);
        if (idempotentAction.isPresent()) {
            actionService.incrementSharingCounter(idempotentAction.get().getActionBase().getId(), 1);
            return waitForSharedActionToFinish(actionDto);
        } else {
             ActionDto action = ActionDtoUtils.actionToDto(actionService.saveAction(actionDto), actionDto);
             return performAction(action);
        }
    }

    private void setActionName(ActionScheduleRequest request) {
        if (request.getAction().getName() == null || request.getAction().getName().isEmpty()) {
            Class<?> actionClass = request.getAction().getClass();
            if (actionClass.isAnnotationPresent(Action.class)) {
                request.getAction().setName(actionClass.getAnnotation(Action.class).name());
            } else if (actionClass.isAnnotationPresent(ComplexAction.class)) {
                request.getAction().setName(actionClass.getAnnotation(ComplexAction.class).name());
            }
        }
    }
}
