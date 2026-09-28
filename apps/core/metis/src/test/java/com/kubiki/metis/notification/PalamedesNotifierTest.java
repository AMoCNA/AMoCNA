package com.kubiki.metis.notification;

import com.kubiki.common.model.GraphUpdateMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PalamedesNotifierTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Test
    void notify_retriesUntilPublishSucceeds() {
        doThrow(new RuntimeException("Connection refused"))
                .doThrow(new RuntimeException("Connection refused"))
                .doNothing()
                .when(rabbitTemplate)
                .convertAndSend(
                        eq(PalamedesNotifier.EXCHANGE),
                        eq(PalamedesNotifier.ROUTING_KEY),
                        any(GraphUpdateMessage.class));

        PalamedesNotifier notifier = new PalamedesNotifier(rabbitTemplate);
        notifier.notify("http://example.org/pod", "http://example.org/Pod", "CREATED", "corr-1");

        ArgumentCaptor<GraphUpdateMessage> messageCaptor = ArgumentCaptor.forClass(GraphUpdateMessage.class);
        verify(rabbitTemplate, times(3)).convertAndSend(
                eq(PalamedesNotifier.EXCHANGE),
                eq(PalamedesNotifier.ROUTING_KEY),
                messageCaptor.capture());
        assertThat(messageCaptor.getValue().resourceIri()).isEqualTo("http://example.org/pod");
    }
}
