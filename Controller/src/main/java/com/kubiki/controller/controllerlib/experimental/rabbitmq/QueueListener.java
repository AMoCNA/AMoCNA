package com.kubiki.controller.controllerlib.experimental.rabbitmq;

import com.kubiki.controller.commons.definitons.ActionScheduleRequest;
import com.kubiki.controller.controllerlib.service.ActionService;
import lombok.AllArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
@ConditionalOnProperty(name = "actions.rabbit.enabled", havingValue = "true")
public class QueueListener {

    private final ActionService actionService;
    // czytam sb teraz gita i ten exchange chyba nie matchuje sie z defaultem - do sprawdzenia w przyszlosci
    @RabbitListener(queues = {"q.kubiki.actions"})
    public void handleRequest(ActionScheduleRequest request) {
        actionService.handleActionRequest(request);
    }
}
