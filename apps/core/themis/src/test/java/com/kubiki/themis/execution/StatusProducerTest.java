package com.kubiki.themis.execution;

import com.kubiki.common.model.ActionStatusUpdate;
import com.kubiki.common.model.ExecutionStatus;
import com.kubiki.themis.config.RabbitMQConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StatusProducerTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private StatusProducer statusProducer;

    @Test
    void shouldSendUpdateToRabbitMQ() {
        ActionStatusUpdate update = new ActionStatusUpdate("action1", ExecutionStatus.COMPLETED, null, 200);

        statusProducer.sendUpdate(update);

        verify(rabbitTemplate).convertAndSend(RabbitMQConfig.EXCHANGE, "status", update);
    }
}
