package com.kubiki.palamedes.saga;

import com.kubiki.common.model.ActionStatusUpdate;
import com.kubiki.common.model.ExecutionStatus;
import com.kubiki.common.ontology.OntologyRegistry;
import com.kubiki.palamedes.condition.ConditionFactory;
import com.kubiki.palamedes.condition.ConditionStrategy;
import com.kubiki.palamedes.knowledge.GraphDBGateway;
import com.kubiki.palamedes.model.ActionData;
import com.kubiki.palamedes.pipeline.EngineWakeupEvent;
import lombok.RequiredArgsConstructor;
import org.eclipse.rdf4j.model.IRI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * SagaManager (MAPE-Monitor/Analyze):
 * Handles execution feedback from Themis and manages workflow state/compensations.
 * Evaluates Post-conditions for verification.
 */
@Service
@RequiredArgsConstructor
public class SagaManager {
    private static final Logger log = LoggerFactory.getLogger(SagaManager.class);
    private final GraphDBGateway gateway;
    private final OntologyRegistry ontologyRegistry;
    private final ConditionFactory conditionFactory;
    private final ApplicationEventPublisher publisher;
    private final SagaTransitionHandler transitionHandler;

    public void handleFeedback(ActionStatusUpdate update) {
        log.info("SagaManager: Handling feedback: {}", update);

        IRI actionIri = ontologyRegistry.actionsOntology(update.actionId());

        if (update.status() == ExecutionStatus.COMPLETED) {
            // 1. VERIFICATION: Evaluate Post-conditions
            log.info("SagaManager: Action completed. Evaluating post-conditions...");
            if (verifyPostConditions(actionIri)) {
                log.info("SagaManager: Action succeeded and verified");
                gateway.updateExecutionStatus(actionIri, "COMPLETED");
                transitionHandler.processSuccessTransition(actionIri);
            } else {
                log.error("SagaManager: Action completed but POST-CONDITIONS FAILED");
                gateway.updateExecutionStatus(actionIri, "FAILED_INTERNAL");
                transitionHandler.processFailureTransition(actionIri);
            }
        } else {
            log.error("SagaManager: Action failed: {}", update.status());
            gateway.updateExecutionStatus(actionIri, update.status().name());
            transitionHandler.processFailureTransition(actionIri);
        }

        log.info("SagaManager: Publishing EngineWakeupEvent after feedback update");
        publisher.publishEvent(new EngineWakeupEvent("Saga state updated from Themis feedback"));
    }

    private boolean verifyPostConditions(IRI actionIri) {
        ActionData data = gateway.fetchActionStructure(actionIri);
        if (data == null || data.postConditions().isEmpty()) {
            return true;
        }

        log.info("Verifying {} post-conditions for action {}", data.postConditions().size(), actionIri);

        for (ActionData.Condition cond : data.postConditions()) {
            Optional<ConditionStrategy> strategy = conditionFactory.getStrategy(cond.type());
            if (strategy.isPresent()) {
                try {
                    if (!strategy.get().evaluate(cond)) {
                        log.warn("Post-condition {} NOT MET", cond.id());
                        return false;
                    }
                } catch (Exception e) {
                    log.error("Error evaluating post-condition {}: {}", cond.id(), e.getMessage());
                    return false;
                }
            } else {
                log.error("No strategy for post-condition type {}", cond.type());
                return false;
            }
        }
        return true;
    }
}
