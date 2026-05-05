package com.kubiki.themis.execution;

import com.kubiki.themis.model.ActionData;
import com.kubiki.themis.saga.SagaEngine;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ActionDispatcher {
    private final Map<String, ProtocolExecutor> protocolExecutors;

    public ActionDispatcher(List<ProtocolExecutor> executors) {
        this.protocolExecutors = executors.stream()
            .collect(Collectors.toMap(ProtocolExecutor::getSupportedProtocol, Function.identity()));
    }

    public boolean dispatch(ActionData action, UUID executionId) {
        return switch (action) {
            case ActionData.SimpleAction s -> executeSimple(s, executionId);
            case ActionData.ComplexWorkflow c -> executeWorkflow(c, executionId);
        };
    }

    private boolean executeSimple(ActionData.SimpleAction action, UUID executionId) {
        ProtocolExecutor executor = protocolExecutors.get(action.protocol());
        if (executor == null) {
            System.err.println("Unsupported protocol: " + action.protocol());
            return false;
        }
        return executor.execute(action, executionId);
    }

    private boolean executeWorkflow(ActionData.ComplexWorkflow workflow, UUID executionId) {
        SagaEngine saga = new SagaEngine();
        for (ActionData step : workflow.steps()) {
            if (step instanceof ActionData.SimpleAction s) {
                ProtocolExecutor executor = protocolExecutors.get(s.protocol());
                saga.addStep(new SagaEngine.Step(s.id(), executor, s, executionId));
            }
        }
        return saga.run();
    }
}
