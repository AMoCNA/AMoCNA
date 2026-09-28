package com.kubiki.palamedes.dispatcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kubiki.common.config.AmocnaCommonProperties;
import com.kubiki.common.model.ActionMessage;
import com.kubiki.palamedes.config.RabbitMQConfig;
import com.kubiki.palamedes.knowledge.SparqlRepository;
import com.kubiki.palamedes.utils.ActionUtils;
import lombok.RequiredArgsConstructor;
import org.eclipse.rdf4j.query.BindingSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OutboxService {
    private static final Logger log = LoggerFactory.getLogger(OutboxService.class);

    private final SparqlRepository sparqlRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final AmocnaCommonProperties properties;
    private final ActionUtils actionUtils;

    public void save(ActionMessage message) {
        try {
            String eventIri = actionUtils.generateOutboxEventId(properties.ontology().actionsNamespace());
            String payload = objectMapper.writeValueAsString(message);
            String timestamp = Instant.now().toString();

            log.info("Outbox: Saving action message to outbox for action {}", message.actionId());
            sparqlRepository.saveOutboxEvent(eventIri, payload, timestamp);
        } catch (Exception e) {
            log.error("Outbox: Failed to save action message to outbox", e);
            throw new RuntimeException(e);
        }
    }

    @Scheduled(fixedDelayString = "${palamedes.dispatcher.outbox-poll-rate-ms:500}")
    public void processOutbox() {
        List<BindingSet> pending;
        try {
            pending = sparqlRepository.findPendingEvents();
        } catch (Exception e) {
            log.error("Outbox: Failed to query pending outbox events", e);
            return;
        }

        if (pending.isEmpty()) {
            return;
        }

        log.debug("Outbox: Found {} pending outbox events", pending.size());
        for (BindingSet bs : pending) {
            String eventUri = bs.getValue("event").stringValue();
            String payload = bs.getValue("payload").stringValue();

            try {
                ActionMessage message = objectMapper.readValue(payload, ActionMessage.class);
                log.info("Outbox: Processing outbox event {} for action {}", eventUri, message.actionId());

                rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ACTION_ROUTING_KEY, message, m -> {
                    m.getMessageProperties().setPriority(message.priority());
                    return m;
                });

                log.info("Outbox: Successfully dispatched action {}, deleting event {}", message.actionId(), eventUri);
                sparqlRepository.deleteOutboxEvent(eventUri);
            } catch (Exception e) {
                log.error("Outbox: Failed to process outbox event {}", eventUri, e);
            }
        }
    }
}
