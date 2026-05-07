package com.kubiki.themis.condition.impl;

import com.kubiki.themis.condition.ConditionEvaluator;
import com.kubiki.themis.config.ThemisProperties;
import com.kubiki.themis.constants.OntologyConstants;
import com.kubiki.themis.knowledge.GraphDBGateway;
import com.kubiki.themis.model.ActionData;
import org.springframework.stereotype.Component;

@Component
public class SparqlConditionEvaluator implements ConditionEvaluator {
    private final GraphDBGateway graphDBGateway;
    private final ThemisProperties properties;

    public SparqlConditionEvaluator(GraphDBGateway graphDBGateway, ThemisProperties properties) {
        this.graphDBGateway = graphDBGateway;
        this.properties = properties;
    }

    @Override
    public boolean supports(String conditionType) {
        String stateBasedCondition = properties.ontology().moaNamespace() + OntologyConstants.CLASS_STATE_BASED_CONDITION;
        return stateBasedCondition.equals(conditionType);
    }

    @Override
    public boolean evaluate(ActionData.ConditionData condition) {
        return graphDBGateway.executeConditionQuery(condition.policy());
    }
}
