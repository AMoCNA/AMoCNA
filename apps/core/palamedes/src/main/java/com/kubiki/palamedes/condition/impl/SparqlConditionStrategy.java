package com.kubiki.palamedes.condition.impl;

import com.kubiki.common.config.AmocnaCommonProperties;
import com.kubiki.palamedes.condition.ConditionStrategy;
import com.kubiki.palamedes.knowledge.GraphDBGateway;
import com.kubiki.palamedes.model.ActionData;
import lombok.RequiredArgsConstructor;
import org.eclipse.rdf4j.model.IRI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SparqlConditionStrategy implements ConditionStrategy {
    private static final Logger log = LoggerFactory.getLogger(SparqlConditionStrategy.class);
    private static final String CLASS_STATE_BASED_CONDITION = "StateBasedCondition";

    private final GraphDBGateway gateway;
    private final AmocnaCommonProperties properties;


    @Override
    public boolean supports(IRI conditionType) {
        String namespace = properties.ontology().actionsNamespace();
        return (namespace + CLASS_STATE_BASED_CONDITION).equals(conditionType.stringValue());
    }

    @Override
    public boolean evaluate(ActionData.Condition condition) {
        log.debug("Evaluating SPARQL condition: {}", condition.id());
        try {
            return gateway.executeConditionQuery(condition.policy());
        } catch (Exception e) {
            log.error("Error evaluating SPARQL condition {}: {}", condition.id(), e.getMessage());
            return false;
        }
    }
}
