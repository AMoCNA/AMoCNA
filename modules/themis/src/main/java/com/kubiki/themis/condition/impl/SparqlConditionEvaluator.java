package com.kubiki.themis.condition.impl;

import com.kubiki.themis.condition.ConditionEvaluator;
import com.kubiki.themis.knowledge.GraphDBGateway;
import com.kubiki.themis.model.ActionData;
import org.springframework.stereotype.Component;

@Component
public class SparqlConditionEvaluator implements ConditionEvaluator {
    private final GraphDBGateway graphDBGateway;
    private static final String STATE_BASED_CONDITION = "http://www.semanticweb.org/patryk/ontologies/2026/4/MoaMont#StateBasedCondition";

    public SparqlConditionEvaluator(GraphDBGateway graphDBGateway) {
        this.graphDBGateway = graphDBGateway;
    }

    @Override
    public boolean supports(String conditionType) {
        return STATE_BASED_CONDITION.equals(conditionType);
    }

    @Override
    public boolean evaluate(ActionData.ConditionData condition) {
        return graphDBGateway.executeConditionQuery(condition.policy());
    }
}
