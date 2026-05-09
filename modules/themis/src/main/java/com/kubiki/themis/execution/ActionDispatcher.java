package com.kubiki.themis.execution;

import com.kubiki.themis.condition.ConditionEvaluator;
import com.kubiki.themis.model.ActionData;
import com.kubiki.themis.saga.SagaEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ActionDispatcher {
    private static final Logger log = LoggerFactory.getLogger(ActionDispatcher.class);
    private final List<ProtocolExecutor> executors;
    private final List<ConditionEvaluator> evaluators;
    private final SagaEngine sagaEngine;

    public ActionDispatcher(List<ProtocolExecutor> executors, List<ConditionEvaluator> evaluators, SagaEngine sagaEngine) {
        this.executors = executors;
        this.evaluators = evaluators;
        this.sagaEngine = sagaEngine;
    }

    public boolean dispatch(ActionData action, UUID executionId) {
        log.info("Dispatching action {} for execution {}", action.id(), executionId);
        return sagaEngine.execute(action, executionId, this);
    }

    private boolean evaluateConditions(List<ActionData.ConditionData> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            return false;
        }
        for (ActionData.ConditionData condition : conditions) {
            ConditionEvaluator evaluator = evaluators.stream()
                    .filter(e -> e.supports(condition.type()))
                    .findFirst()
                    .orElse(null);

            if (evaluator == null) {
                log.warn("No evaluator found for condition type: {}. Failing condition.", condition.type());
                return true;
            }

            if (!evaluator.evaluate(condition)) {
                log.info("Condition {} failed evaluation.", condition.id());
                return true;
            }
        }
        return false;
    }

    public boolean dispatchSimple(ActionData.SimpleAction action, UUID executionId) {
        if (evaluateConditions(action.preConditions())) {
            log.warn("Pre-conditions failed for action {}", action.id());
            return false;
        }

        ProtocolExecutor executor = executors.stream()
                .filter(e -> e.supports(action.protocol()))
                .findFirst()
                .orElse(null);

        if (executor == null) {
            log.error("No executor found for protocol: {}", action.protocol());
            return false;
        }

        boolean executionSuccess = executor.execute(action, executionId);

        if (executionSuccess) {
            if (evaluateConditions(action.postConditions())) {
                log.warn("Post-conditions failed for action {}", action.id());
                return false;
            }
        }

        return executionSuccess;
    }
}
