package com.kubiki.palamedes.listener;

import com.kubiki.common.logging.LogLoopStep;
import com.kubiki.common.logging.LoopPhase;
import com.kubiki.common.logging.MdcContext;
import com.kubiki.common.logging.MdcParam;
import com.kubiki.common.model.ActionStatusUpdate;
import com.kubiki.palamedes.config.RabbitMQConfig;
import com.kubiki.palamedes.saga.SagaManager;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class ActionStatusListener {
    private final SagaManager sagaManager;

    public ActionStatusListener(SagaManager sagaManager) {
        this.sagaManager = sagaManager;
    }

    @RabbitListener(queues = RabbitMQConfig.STATUS_QUEUE)
    @MdcContext
    @LogLoopStep(
            phase = LoopPhase.FEEDBACK,
            step = "Received Action Status Update",
            actionId = "#message.actionId()",
            details = "'status=' + #message.status() + ', observedStatusCode=' + #message.observedStatusCode()"
    )
    public void receiveStatus(
            @MdcParam(value = "actionId", property = "actionId")
            @MdcParam(value = "status", property = "status") ActionStatusUpdate message) {
        sagaManager.handleFeedback(message);
    }
}
