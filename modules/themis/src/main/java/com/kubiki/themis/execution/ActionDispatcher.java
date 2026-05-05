package com.kubiki.themis.execution;

import com.kubiki.themis.model.ActionData;
import com.kubiki.themis.saga.SagaEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ActionDispatcher {
    private static final Logger logger = LoggerFactory.getLogger(ActionDispatcher.class);
    private final Map<String, ActionExecutor> simpleExecutors;

    public ActionDispatcher(List<ActionExecutor> executors) {
        this.simpleExecutors = executors.stream()
            .collect(Collectors.toMap(ActionExecutor::getActionType, Function.identity()));
    }

    public boolean dispatch(ActionData action) {
        return switch (action) {
            case ActionData.SimpleAction s -> executeSimple(s);
            case ActionData.ComplexWorkflow c -> executeWorkflow(c);
        };
    }

    private boolean executeSimple(ActionData.SimpleAction action) {
        ActionExecutor executor = simpleExecutors.get(action.functionalIntent());
        if (executor == null) {
            logger.error("No executor for intent: {}", action.functionalIntent());
            return false;
        }
        return executor.execute(action.targetIri());
    }

    private boolean executeWorkflow(ActionData.ComplexWorkflow workflow) {
        SagaEngine saga = new SagaEngine();
        for (ActionData step : workflow.steps()) {
            if (step instanceof ActionData.SimpleAction s) {
                ActionExecutor executor = simpleExecutors.get(s.functionalIntent());
                if (executor != null) {
                    saga.addStep(new SagaEngine.Step(s.id(), executor, s.targetIri()));
                } else {
                    logger.error("No executor for step: {} (intent: {})", s.id(), s.functionalIntent());
                    // Depending on policy, we might fail here or continue. 
                    // For a saga, we probably want to fail fast if we can't build it.
                    return false;
                }
            } else {
                // Nested ComplexWorkflows are possible in the model but not handled in this basic dispatcher yet.
                logger.warn("Nested workflows are not supported in this MVP version: {}", step.id());
            }
        }
        return saga.run();
    }
}
