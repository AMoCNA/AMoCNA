package com.kubiki.themis.execution;

import com.kubiki.common.model.ActionMessage;
import com.kubiki.themis.config.RabbitMQConfig;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.kubiki.common.logging.MdcContext;
import com.kubiki.common.logging.MdcParam;
import com.kubiki.common.logging.ObserveSLO;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ActionQueueListener {
    private static final Logger log = LoggerFactory.getLogger(ActionQueueListener.class);
    private final ActionExecutionHandler executionHandler;

    @MdcContext
    @RabbitListener(queues = RabbitMQConfig.ACTION_QUEUE)
    @ObserveSLO(name = "themis.action.slo", thresholdMs = 10000)
    @Timed(value = "themis.queue.receive", description = "Time taken to process action from queue")
    public void receiveAction(
            @MdcParam(value = "actionId", property = "actionId")
            @MdcParam(value = "protocol", property = "protocol") ActionMessage message) {
        executionHandler.executeAndVerify(message);
    }
}
