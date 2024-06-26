package com.kubiki.controller.controllerlib.storage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kubiki.controller.commons.definitons.ActionBase;
import com.kubiki.controller.commons.definitons.ActionState;
import com.kubiki.controller.controllerlib.data.dao.action.ActionDaoImpl;
import com.kubiki.controller.controllerlib.data.entity.Action;
import com.kubiki.controller.controllerlib.data.entity.ActionEntity;
import com.kubiki.controller.controllerlib.data.utils.ActionDtoUtils;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;


import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@Log4j2
@AllArgsConstructor
public class ActionStorage {

    private final ActionDaoImpl actionDao;
    private final LockProvider lockProvider;

    public ActionEntity getAction(Long id) {
        return (ActionEntity) actionDao.findById(id);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    @Retryable(maxAttempts = 10,  backoff = @Backoff(random = true, delay = 5, maxDelay = 100, multiplier = 1.5))
    public Optional<ActionEntity> saveAndScheduleAction(ActionDto actionDto) {
        String key = StringUtils.isEmpty(actionDto.getIdempotencyKey()) ? "idempotency-key" : actionDto.getIdempotencyKey();
        Optional<ActionDto> idempotentAction = getIdempotentAction(actionDto);
        if (idempotentAction.isEmpty()) {
            ActionEntity result = saveAction(actionDto);
            return Optional.of(result);
        } else {
           return Optional.empty();
        }
    }

    @Deprecated
    private void accessLock(String key) {
        lockProvider.acquireLock(key);
        actionDao.lock(key);
    }

    @Deprecated
    private void releaseLock(String key) {
        lockProvider.releaseLock(key);
        actionDao.release(key);
    }

    public ActionEntity saveAction(ActionDto actionDto) {
        ActionBase actionBase = actionDto.getActionBase();
        //default value
        if(actionBase.getState() == null) {
            actionBase.setArgs(ActionState.STARTED);
        }

        ActionEntity entity = saveAction(
                actionBase.getName(),
                actionBase.getState(),
                actionDto.getIdempotencyKey(),
                new Date(),
                actionDto.getPriority(),
                actionBase.getParentId(),
                1,
                convertArgumentsToJSONString(actionBase.getArgs()));

        actionDto.setSharingCounter(entity.getSharingCounter());
        return entity;
    }

    public ActionEntity saveAction(String actionName, ActionState actionState, String idempotencyId, Date performTime, int priority, String parentId, int sharingCounter, String arguments) {
        ActionEntity action = new ActionEntity();
        action.setActionName(actionName);
        action.setState(actionState);
        action.setPerformTime(performTime);
        action.setIdempotencyId(idempotencyId);
        action.setPriority(priority);
        action.setParentId(parentId);
        action.setSharingCounter(sharingCounter);
        action.setArguments(arguments);
        actionDao.create(action);
        return action;
    }

    public Optional<ActionDto> getIdempotentAction(ActionDto actionDto) {
        ActionBase actionBase = actionDto.getActionBase();
        if (Objects.isNull(actionDto.getIdempotencyKey()) || Objects.isNull(actionBase.getIdempotencyWindow())) {
            return Optional.empty();
        }
        List<Action> sharedActions = getSharedActions(new Date(), actionBase.getIdempotencyWindow(), actionDto.getIdempotencyKey(), actionDto.getPriority());

        if(!sharedActions.isEmpty()) {
            log.info("Shared action with {}", ActionDtoUtils.actionToDto(sharedActions.get(0), actionDto));
            return Optional.of(ActionDtoUtils.actionToDto(sharedActions.get(0), actionDto));
        } else {
            return Optional.empty();
        }
    }

    private List<Action> getSharedActions(Date time, Long offset, String idempotencyId, int priority) {
        return actionDao.findByPerformTimeBeforeAndIdempotencyId(time, offset, idempotencyId, priority);
    }

    @Transactional
    public Integer updateSharingCounter(Long id, Integer counter) {
        ActionEntity actionEntity = (ActionEntity) actionDao.findById(id);
        actionEntity.setSharingCounter(actionEntity.getSharingCounter() + counter);
        actionDao.update(actionEntity);
        return actionEntity.getSharingCounter();
    }

    public ActionDto updateActionFromDto(ActionDto actionDto) {
        ActionEntity actionToUpdate = getAction(actionDto.getActionBase().getId());
        ActionBase actionBase = actionDto.getActionBase();

        actionToUpdate.setState(actionBase.getState());
        actionToUpdate.setSharingCounter(actionDto.getSharingCounter());
        actionToUpdate.setSuccessPerformTime(actionDto.getSuccessPerformTime());
        actionToUpdate.setLastTryTime(actionDto.getLastTryTime());

        updateAction(actionToUpdate);
        return ActionDtoUtils.actionToDto(actionToUpdate, actionDto);
    }

    public String convertArgumentsToJSONString(Object arguments) {
        String json = null;
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            json = objectMapper.writeValueAsString(arguments);
        } catch (JsonProcessingException e) {
            log.error("Cannot parse object to JSON!");
        }
        return json;
    }

    private void updateAction(ActionEntity action) {
        actionDao.update(action);
    }
}
