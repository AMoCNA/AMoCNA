package com.kubiki.controller.controllerlib.data.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kubiki.controller.commons.definitons.ActionBase;
import com.kubiki.controller.controllerlib.data.entity.Action;
import com.kubiki.controller.controllerlib.storage.ActionDto;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ActionDtoUtils {
    public ActionDto actionToDto(Action action, ActionDto oldActionDto) {
        if (oldActionDto != null) {
            ActionBase oldActionBase = oldActionDto.getActionBase();
            oldActionDto.setActionBase(new ActionBase(
                    action.getId(),
                    action.getActionName(),
                    convertJSONStringToObject(action.getArguments()),
                    action.getState(),
                    oldActionBase.getRetryNum(),
                    oldActionBase.getIdempotencyWindow(),
                    action.getParentId())
            );

            oldActionDto.setSharingCounter(action.getSharingCounter());
            oldActionDto.setIdempotencyKey(action.getIdempotencyId());
            oldActionDto.setPriority(action.getPriority());
        }

        ActionDto actionDto = new ActionDto();
        ActionBase actionBase = new ActionBase(
                action.getId(),
                action.getActionName(),
                convertJSONStringToObject(action.getArguments()),
                action.getState(),
                0,
                0L,
                action.getParentId()
        );

        actionDto.setActionBase(actionBase);
        actionDto.setSharingCounter(action.getSharingCounter());
        actionDto.setIdempotencyKey(action.getIdempotencyId());
        actionDto.setPriority(action.getPriority());

        return actionDto;
    }

    public Object convertJSONStringToObject(String arguments) {
        //use it for complex actions
        Object object = null;
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            object = objectMapper.readValue(arguments, Object.class);
        } catch (JsonProcessingException e) {
            System.out.println("Cannot parse string to object!");
        }
        return object;
    }
}
