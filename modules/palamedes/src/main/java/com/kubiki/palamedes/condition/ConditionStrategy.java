package com.kubiki.palamedes.condition;

import com.kubiki.palamedes.model.ActionData;
import org.eclipse.rdf4j.model.IRI;

public interface ConditionStrategy {
    boolean supports(IRI conditionType);

    boolean evaluate(ActionData.Condition condition);
}
