package com.kubiki.themis.condition;

import com.kubiki.themis.model.ActionData;

public interface ConditionEvaluator {
    boolean supports(String conditionType);

    boolean evaluate(ActionData.ConditionData condition);
}
