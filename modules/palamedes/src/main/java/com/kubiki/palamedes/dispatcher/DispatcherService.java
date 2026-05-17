package com.kubiki.palamedes.dispatcher;

import com.kubiki.palamedes.config.RabbitMQConfig;
import com.kubiki.palamedes.knowledge.GraphDBGateway;
import com.kubiki.palamedes.model.ActionMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;


@Service
public class DispatcherService {
    private static final Logger log = LoggerFactory.getLogger(DispatcherService.class);
    private final RabbitTemplate rabbitTemplate;
    private final GraphDBGateway graphDBGateway;

    public DispatcherService(RabbitTemplate rabbitTemplate, GraphDBGateway graphDBGateway) {
        this.rabbitTemplate = rabbitTemplate;
        this.graphDBGateway = graphDBGateway;
    }

    public void dispatch(ActionMessage message) {
        log.info("Dispatching action {} to Themis", message.actionId());
        // Update GraphDB: Set current state to State_InProgress
        // graphDBGateway.updateActionStatus(message.actionId(), "State_InProgress"); 

        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ACTION_ROUTING_KEY, message);
    }
}
