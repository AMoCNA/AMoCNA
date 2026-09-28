package com.kubiki.palamedes.dispatcher;

import com.kubiki.common.logging.LogLoopStep;
import com.kubiki.common.logging.LoopPhase;
import com.kubiki.common.model.ActionMessage;
import com.kubiki.palamedes.config.RabbitMQConfig;
import com.kubiki.palamedes.knowledge.GraphDBGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;


@Service
public class DispatcherService {
    private static final Logger log = LoggerFactory.getLogger(DispatcherService.class);
    private final OutboxService outboxService;
    private final GraphDBGateway graphDBGateway;

    public DispatcherService(OutboxService outboxService, GraphDBGateway graphDBGateway) {
        this.outboxService = outboxService;
        this.graphDBGateway = graphDBGateway;
    }

    @LogLoopStep(
            phase = LoopPhase.DISPATCH,
            step = "Dispatched Action to Queue (via Outbox)",
            actionId = "#message.actionId()",
            resource = "#message.resourceName()",
            details = "'protocol=' + #message.protocol() + ', maxRetries=' + #message.maxRetries()"
    )
    public void dispatch(ActionMessage message) {
        outboxService.save(message);
    }
}
