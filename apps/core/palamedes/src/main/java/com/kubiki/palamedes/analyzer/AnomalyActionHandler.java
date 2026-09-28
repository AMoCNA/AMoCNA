package com.kubiki.palamedes.analyzer;

import com.kubiki.common.logging.IdempotentAction;
import com.kubiki.palamedes.knowledge.ActionRepository;
import lombok.RequiredArgsConstructor;
import org.eclipse.rdf4j.model.IRI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AnomalyActionHandler {
    private static final Logger log = LoggerFactory.getLogger(AnomalyActionHandler.class);
    private final ActionRepository actionRepository;

    @IdempotentAction(
            targetExpression = "#resourceIri",
            intentExpression = "#intentIri",
            cooldownSeconds = 300
    )
    public boolean createActionWorkflow(IRI resourceIri, IRI intentIri, String actionId) {
        log.info("AnomalyActionHandler: Creating action workflow {} for resource {} with intent {}",
                actionId, resourceIri, intentIri);
        actionRepository.createActionWorkflow(resourceIri, intentIri, actionId);
        return true;
    }
}
