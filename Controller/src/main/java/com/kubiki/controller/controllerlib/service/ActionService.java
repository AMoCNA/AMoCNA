package com.kubiki.controller.controllerlib.service;

import com.kubiki.controller.commons.definitons.ActionBase;
import com.kubiki.controller.commons.definitons.ActionResponseDto;
import com.kubiki.controller.commons.definitons.ActionScheduleRequest;
import com.kubiki.controller.commons.definitons.ActionState;
import com.kubiki.controller.controllerlib.data.entity.Action;
import com.kubiki.controller.controllerlib.data.entity.ActionEntity;
import com.kubiki.controller.controllerlib.data.utils.ActionDtoUtils;
import com.kubiki.controller.controllerlib.storage.ActionDto;
import com.kubiki.controller.controllerlib.storage.ActionStorage;
import com.kubiki.controller.controllerlib.storage.ActionViolatesIdempotencySettingsException;
import com.kubiki.controller.controllerlib.storage.LockProvider;
import com.kubiki.controller.controllerlib.utils.TimedFutureCreator;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Optional;


@Service
@AllArgsConstructor
@Log4j2
public class ActionService {
    private final ActionExecutor actionExecutor;
    private final ActionStorage actionStorage;
    private final TimedFutureCreator timedFutureCreator;
    private final LockProvider lockProvider;

    public ActionResponseDto handleActionRequest(ActionScheduleRequest scheduleRequest) {
        try {
            log.info("processing action request: {}", scheduleRequest);
            ActionDto actionDto = new ActionDto(scheduleRequest.getAction(), 1, scheduleRequest.getIdempotencyKey(), scheduleRequest.getPriority(), null, null);
            lockProvider.acquireLock(Optional.ofNullable(actionDto.getIdempotencyKey()).orElse("idempotency-key"));
            Optional<ActionEntity> action = actionStorage.saveAndScheduleAction(actionDto);
            lockProvider.releaseLock(Optional.ofNullable(actionDto.getIdempotencyKey()).orElse("idempotency-key"));
            if (action.isEmpty()) {
                log.info("request {} skipped due to idempotency", scheduleRequest);
                return new ActionResponseDto(null, false, "Idempotency Settings Exception");
            }
            actionDto.getActionBase().setId(action.get().getId());
            timedFutureCreator.runAsync(() -> actionExecutor.performAction(actionDto));
            //we always want to return msg immediately, even though action is failed
            return new ActionResponseDto(action.get().getId(), true, "Success");
        } catch (Exception ex) {
            lockProvider.releaseLock(Optional.ofNullable(scheduleRequest.getIdempotencyKey()).orElse("idempotency-key"));
            return new ActionResponseDto(null, false, "Failure");
        }
    }

    @Transactional
    public ActionEntity saveAction(ActionDto actionDto) {
        ActionBase actionBase = actionDto.getActionBase();
        //default value
        if(actionBase.getState() == null) {
            actionBase.setArgs(ActionState.STARTED);
        }

        ActionEntity entity = this.actionStorage.saveAction(
                actionBase.getName(),
                actionBase.getState(),
                actionDto.getIdempotencyKey(),
                new Date(),
                actionDto.getPriority(),
                actionBase.getParentId(),
                1,
                actionStorage.convertArgumentsToJSONString(actionBase.getArgs()));

        actionDto.setSharingCounter(entity.getSharingCounter());
        return entity;
    }

    public ActionDto refreshAction(ActionDto actionDto) {
        return ActionDtoUtils.actionToDto(actionStorage.getAction(actionDto.getActionBase().getId()), actionDto);
    }

    public ActionDto updateAction(ActionDto actionDto) {
        return actionStorage.updateActionFromDto(actionDto);
    }

    @Transactional
    public Optional<ActionDto> getIdempotentAction(ActionDto actionDto) {
        return actionStorage.getIdempotentAction(actionDto);
    }

    public Integer incrementSharingCounter(Long id, Integer count) {
        return actionStorage.updateSharingCounter(id, count);
    }

    public Integer decrementSharingCounter(Long id, Integer count) {
        return actionStorage.updateSharingCounter(id, -count);
    }
}
