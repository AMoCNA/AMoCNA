package com.kubiki.controller.commons.definitons;

import com.kubiki.controller.commons.annotations.complex.ComplexAction;
import com.kubiki.controller.commons.annotations.simple.Action;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ActionInvoker {

    @Value("${actions.controllers.master.address}")
    private String masterAddress;
    @Value("${actions.rabbitmq.exchange:kubiki.actions.exchange}")
    private String rabbitExchangeName;
    @Value("${actions.rabbitmq.queue:q.kubiki.actions}")
    private String rabbitQueueName;
    private final RabbitTemplate rabbitTemplate;

    public <T extends ActionBase> ActionResponseDto invokeAction(T action) {
        return invokeAction(action, action.getName() + '-' + action.getArgs().toString());
    }

    public <T extends ActionBase> ActionResponseDto invokeAction(T action, String idempotencyKey) {
        return invokeAction(action, idempotencyKey, 0);
    }

    public <T extends ActionBase> ActionResponseDto invokeAction(T action, String idempotencyKey, int idempotencyPriority) {
        return invokeAction(action, idempotencyKey, idempotencyPriority, Math.min(idempotencyPriority * 5, 300));
    }

    public <T extends ActionBase> ActionResponseDto invokeAction(T action, String idempotencyKey, int idempotencyPriority, int delay) {
        ActionScheduleRequest request = ActionScheduleRequest.builder()
                .action(action)
                .idempotencyKey(idempotencyKey)
                .priority(idempotencyPriority)
                .delay(delay)
                .build();
        return sendViaRest(request);
    }

    public ActionResponseDto invokeAction(ActionScheduleRequest request) {
        return sendViaRest(request);
    }

    public void invokeActionsBatch(List<ActionScheduleRequest> actionRequests) {
        actionRequests.forEach(this::setActionName);
        new RestTemplate().postForObject(masterAddress + "actions/schedule-batch", actionRequests, ActionResponseDto.class);
    }

    private ActionResponseDto sendViaRest(ActionScheduleRequest request) {
        setActionName(request);
        return new RestTemplate().postForObject(masterAddress + "actions/schedule", request, ActionResponseDto.class);
    }

    public <T extends ActionBase> ActionResponseDto invokeViaRabbit(T action, String idempotencyKey, int idempotencyPriority, int delay) {
        ActionScheduleRequest request = ActionScheduleRequest.builder()
                .action(action)
                .idempotencyKey(idempotencyKey)
                .priority(idempotencyPriority)
                .delay(delay)
                .build();

        // skipping queue
        if (delay == 0) {
            return invokeAction(request);
        } else {
            sendActionRequestToRabbit(request);
            return new ActionResponseDto(null, true, "Action scheduled");
        }
    }

    public void invokeBatchViaRabbit(List<ActionScheduleRequest> actionRequests) {
        List<ActionScheduleRequest> instantActions = actionRequests.stream().filter(request -> request.getDelay() == 0).toList();
        List<ActionScheduleRequest> delayedActions = actionRequests.stream().filter(request -> request.getDelay() != 0).toList();

        invokeActionsBatch(instantActions);
        delayedActions.forEach(this::sendActionRequestToRabbit);
    }

    private void sendActionRequestToRabbit(ActionScheduleRequest request) {
        setActionName(request);
        rabbitTemplate.convertAndSend(rabbitExchangeName, rabbitQueueName, request, (m) -> {
            m.getMessageProperties().setHeader("x-delay", request.getDelay() * 1000);
            return m;
        });
    }

    // used to get name from annotation to dto if not present
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
