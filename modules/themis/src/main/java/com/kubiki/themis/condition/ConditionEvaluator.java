package com.kubiki.themis.condition;

import com.kubiki.themis.model.ActionData;
import org.eclipse.rdf4j.model.IRI;

public interface ConditionEvaluator {
    boolean supports(IRI conditionType);
    boolean evaluate(ActionData.ConditionData condition);
}
