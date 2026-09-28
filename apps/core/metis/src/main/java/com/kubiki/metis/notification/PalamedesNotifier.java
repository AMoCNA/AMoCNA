package com.kubiki.metis.notification;

import com.kubiki.common.logging.LogLoopStep;
import com.kubiki.common.logging.LoopPhase;
import com.kubiki.common.logging.MdcContext;
import com.kubiki.common.logging.MdcParam;
import com.kubiki.common.model.GraphUpdateMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Publishes graph-update notifications to the {@code amocna.graph.updates} RabbitMQ queue
 * so that Palamedes (and any other interested consumer) can react to knowledge-base changes.
 *
 * <p>Transient publish failures are retried with backoff. After all retries are exhausted,
 * the failure is logged at ERROR and swallowed — a messaging outage must never roll back
 * a successful graph update.
 */
@Component
public class PalamedesNotifier {

    public static final String EXCHANGE = "amocna.topic.exchange";
    public static final String ROUTING_KEY = "graph.updates.metis";
    private static final Logger log = LoggerFactory.getLogger(PalamedesNotifier.class);
    private static final int MAX_PUBLISH_ATTEMPTS = 5;
    private static final long INITIAL_RETRY_DELAY_MS = 1_000;
    private static final long MAX_RETRY_DELAY_MS = 8_000;
    private final RabbitTemplate rabbitTemplate;

    public PalamedesNotifier(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    private static void sleep(long delayMs) {
        try {
            TimeUnit.MILLISECONDS.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Publishes a graph-update notification to RabbitMQ.
     *
     * @param resourceIri   fully-qualified IRI of the affected resource
     * @param ontologyType  fully-qualified CNEEOnt class IRI of the resource
     * @param changeKind    the kind of change (CREATED, UPDATED, STATE_CHANGED, DELETED)
     * @param correlationId correlation identifier from the originating batch
     */
    @MdcContext
    @LogLoopStep(
            phase = LoopPhase.MONITOR,
            step = "Graph Update Published",
            correlationId = "#correlationId",
            resource = "#resourceIri",
            details = "'ontologyType=' + #ontologyType + ', changeKind=' + #changeKind",
            debugOnly = true
    )
    public void notify(
            @MdcParam("resourceIri") String resourceIri,
            @MdcParam("ontologyType") String ontologyType,
            @MdcParam("changeKind") String changeKind,
            @MdcParam("correlationId") String correlationId) {
        GraphUpdateMessage message = new GraphUpdateMessage(
                resourceIri, ontologyType, changeKind, correlationId);

        long delayMs = INITIAL_RETRY_DELAY_MS;
        for (int attempt = 1; attempt <= MAX_PUBLISH_ATTEMPTS; attempt++) {
            try {
                rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, message);
                return;
            } catch (Exception e) {
                if (attempt >= MAX_PUBLISH_ATTEMPTS) {
                    log.error("Failed to publish graph update to RabbitMQ after {} attempts [correlationId={}]: {}",
                            MAX_PUBLISH_ATTEMPTS, correlationId, e.getMessage(), e);
                    return;
                }
                log.warn("Failed to publish graph update to RabbitMQ (attempt {}/{}) [correlationId={}]: {}",
                        attempt, MAX_PUBLISH_ATTEMPTS, correlationId, e.getMessage());
                sleep(delayMs);
                delayMs = Math.min(delayMs * 2, MAX_RETRY_DELAY_MS);
            }
        }
    }
}
