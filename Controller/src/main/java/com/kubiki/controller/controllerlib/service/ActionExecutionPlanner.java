package com.kubiki.controller.controllerlib.service;

import com.kubiki.controller.commons.definitons.ActionResponseDto;
import com.kubiki.controller.commons.definitons.ActionScheduleRequest;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class ActionExecutionPlanner {
    private final ActionsScheduleRequestQueue actionsScheduleRequestQueue;
    private final ActionService actionService;

    public ActionResponseDto scheduleActionRequest(ActionScheduleRequest scheduleRequest) {
        if (scheduleRequest.getDelay() == 0) {
            return actionService.handleActionRequest(scheduleRequest);
        }
        actionsScheduleRequestQueue.put(scheduleRequest);
        return new ActionResponseDto(null, true, "Action scheduled");
    }
}
