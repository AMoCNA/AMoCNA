package com.kubiki.themis.execution;

import com.kubiki.common.model.ActionStatusUpdate;
import com.kubiki.themis.config.RabbitMQConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class StatusProducer {
    private final RabbitTemplate rabbitTemplate;

    public StatusProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void sendUpdate(ActionStatusUpdate update) {
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.STATUS_ROUTING_KEY, update);
    }
}
