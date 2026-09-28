package com.kubiki.palamedes.listener;

import com.kubiki.common.logging.LogLoopStep;
import com.kubiki.common.logging.LoopPhase;
import com.kubiki.common.logging.MdcContext;
import com.kubiki.common.logging.MdcParam;
import com.kubiki.common.model.GraphUpdateMessage;
import com.kubiki.palamedes.analyzer.AnomalyAgent;
import com.kubiki.palamedes.analyzer.RegistryCredentialPlanner;
import com.kubiki.palamedes.config.RabbitMQConfig;
import com.kubiki.palamedes.pipeline.EngineWakeupEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Listens for graph-update notifications from Metis on the
 * {@code amocna.graph.updates} RabbitMQ queue and triggers the
 * anomaly analysis pipeline immediately.
 *
 * <p>This makes Palamedes event-driven: instead of polling GraphDB every N seconds,
 * it reacts within milliseconds of a knowledge-base change.
 */
@Component
public class GraphUpdateListener {

    private static final Logger log = LoggerFactory.getLogger(GraphUpdateListener.class);

    private final AnomalyAgent anomalyAgent;
    private final RegistryCredentialPlanner registryCredentialPlanner;
    private final ApplicationEventPublisher publisher;

    public GraphUpdateListener(AnomalyAgent anomalyAgent,
                               RegistryCredentialPlanner registryCredentialPlanner,
                               ApplicationEventPublisher publisher) {
        this.anomalyAgent = anomalyAgent;
        this.registryCredentialPlanner = registryCredentialPlanner;
        this.publisher = publisher;
    }

    @RabbitListener(queues = RabbitMQConfig.GRAPH_UPDATES_QUEUE)
    @MdcContext
    @LogLoopStep(
            phase = LoopPhase.ANALYZE,
            step = "Received Graph Update",
            correlationId = "#message.correlationId()",
            resource = "#message.resourceIri()",
            details = "'routingKey=' + #routingKey + ', changeKind=' + #message.changeKind()"
    )
    public void onGraphUpdate(
            @MdcParam(value = "correlationId", property = "correlationId")
            @MdcParam(value = "resourceIri", property = "resourceIri")
            @MdcParam(value = "changeKind", property = "changeKind")
            @MdcParam(value = "ontologyType", property = "ontologyType") GraphUpdateMessage message,
            @MdcParam("routingKey") @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey) {

        anomalyAgent.analyze();

        if (registryCredentialPlanner.scanAndPlan()) {
            publisher.publishEvent(new EngineWakeupEvent("Registry credential remediation planned"));
        }
    }
}
