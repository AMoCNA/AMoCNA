package com.kubiki.themis.saga;

import com.kubiki.themis.execution.ActionDispatcher;
import com.kubiki.themis.knowledge.GraphDBGateway;
import com.kubiki.themis.model.ActionData;
import com.kubiki.themis.model.ExecutionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Stack;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class SagaEngine {
    private static final Logger log = LoggerFactory.getLogger(SagaEngine.class);
    private final GraphDBGateway gateway;
    private final ExecutorService executor;

    public SagaEngine(GraphDBGateway gateway) {
        this.gateway = gateway;
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    public boolean execute(ActionData action, UUID executionId, ActionDispatcher dispatcher) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return executeInternal(action, executionId, dispatcher);
            } catch (Exception e) {
                log.error("Saga execution failed exceptionally", e);
                return false;
            }
        }, executor).join();
    }

    private boolean executeInternal(ActionData action, UUID executionId, ActionDispatcher dispatcher) {
        gateway.updateActionState(action.id(), ExecutionStatus.IN_PROGRESS);

        if (action instanceof ActionData.SimpleAction simple) {
            boolean success = dispatcher.dispatchSimple(simple, executionId);
            gateway.updateActionState(action.id(), success ? ExecutionStatus.SUCCESS : ExecutionStatus.FAILED);
            return success;
        } else if (action instanceof ActionData.ComplexWorkflow workflow) {
            Stack<ActionData> executedSteps = new Stack<>();

            for (ActionData step : workflow.steps()) {
                boolean stepSuccess = executeInternal(step, executionId, dispatcher);

                if (stepSuccess) {
                    executedSteps.push(step);
                } else {
                    log.warn("Workflow {} failed at step {}", workflow.id(), step.id());
                    gateway.updateActionState(workflow.id(), ExecutionStatus.FAILED);
                    compensate(executedSteps, workflow, executionId, dispatcher);
                    return false;
                }
            }

            gateway.updateActionState(workflow.id(), ExecutionStatus.SUCCESS);
            return true;
        }
        return false;
    }

    private void compensate(Stack<ActionData> executedSteps, ActionData.ComplexWorkflow workflow, UUID executionId, ActionDispatcher dispatcher) {
        log.info("Starting compensation for workflow {}", workflow.id());
        while (!executedSteps.isEmpty()) {
            ActionData step = executedSteps.pop();
            ActionData compensation = workflow.compensations().get(step.id());

            if (compensation != null) {
                log.info("Executing compensation {} for step {}", compensation.id(), step.id());
                boolean compSuccess = executeInternal(compensation, executionId, dispatcher);
                if (!compSuccess) {
                    log.error("CRITICAL: Compensation {} failed for step {}!", compensation.id(), step.id());
                    // In a real system we might alert ops, retry, etc.
                }
            } else {
                log.debug("No compensation defined for step {}", step.id());
            }
        }
    }
}
