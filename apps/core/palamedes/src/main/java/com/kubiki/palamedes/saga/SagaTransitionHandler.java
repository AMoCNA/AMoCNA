package com.kubiki.palamedes.saga;

import com.kubiki.common.logging.LogLoopStep;
import com.kubiki.common.logging.LoopPhase;
import com.kubiki.common.logging.StateTransition;
import com.kubiki.palamedes.knowledge.GraphDBGateway;
import com.kubiki.palamedes.knowledge.StateRepository;
import com.kubiki.palamedes.model.ActionData;
import com.kubiki.palamedes.model.WorkflowState;
import com.kubiki.palamedes.model.WorkflowStateMapper;
import com.kubiki.palamedes.utils.ActionUtils;
import lombok.RequiredArgsConstructor;
import org.eclipse.rdf4j.model.IRI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class SagaTransitionHandler {
    private static final Logger log = LoggerFactory.getLogger(SagaTransitionHandler.class);
    private final ActionUtils utils;
    private final GraphDBGateway gateway;
    private final StateRepository stateRepository;
    private final WorkflowStateMapper mapper;

    @StateTransition(
            from = "IN_PROGRESS",
            to = "SUCCEEDED",
            targetExpression = "#actionIri"
    )
    @LogLoopStep(
            phase = LoopPhase.FEEDBACK,
            step = "Saga State Transition: IN_PROGRESS -> SUCCEEDED",
            actionId = "#actionIri.stringValue()"
    )
    public void processSuccessTransition(IRI actionIri) {
        // 2. DEFENSE IN DEPTH: Clear the anomaly state from the targeted resource
        ActionData data = gateway.fetchActionStructure(actionIri);
        if (data != null && data.target() != null) {
            log.debug("SagaTransitionHandler: Clearing anomaly state from resource target: {}", data.target());
            gateway.clearResourceState(data.target());
        }

        processSuccess(actionIri);
    }

    @StateTransition(
            from = "IN_PROGRESS",
            to = "FAILED",
            targetExpression = "#actionIri"
    )
    @LogLoopStep(
            phase = LoopPhase.FEEDBACK,
            step = "Saga State Transition: IN_PROGRESS -> FAILED",
            actionId = "#actionIri.stringValue()"
    )
    public void processFailureTransition(IRI actionIri) {
        // 1. Mark parent as COMPENSATING
        IRI parentIri = gateway.findParent(actionIri);
        if (parentIri != null) {
            stateRepository.transition(parentIri, WorkflowState.PLANNED, WorkflowState.COMPENSATING);
            stateRepository.transition(parentIri, WorkflowState.IN_PROGRESS, WorkflowState.COMPENSATING);

            // Rollback all completed siblings
            List<IRI> siblings = gateway.findChildren(parentIri);
            for (IRI sibling : siblings) {
                if (gateway.getState(sibling) == WorkflowState.SUCCEEDED) {
                    triggerCompensation(sibling);
                }
            }
        }

        // 2. Trigger Compensation for the failed action itself (if applicable)
        triggerCompensation(actionIri);
    }

    private void processSuccess(IRI actionIri) {
        // A. Unlock next sibling in the sequence
        log.debug("Looking for steps dependent on {}", actionIri);
        List<IRI> dependents = gateway.findDependents(actionIri);

        if (!dependents.isEmpty()) {
            for (IRI dependent : dependents) {
                log.debug("Unlocking dependent step {}", dependent);
                gateway.transitionState(dependent, mapper.getFragment(WorkflowState.INITIAL));
            }
        } else {
            // B. If no siblings, check if we need to complete the parent (Join Logic)
            IRI parentIri = gateway.findParent(actionIri);
            if (parentIri != null) {
                log.debug("No more siblings. Checking parent workflow {}", parentIri);
                checkParentCompletion(parentIri);
            }
        }
    }

    private void triggerCompensation(IRI actionIri) {
        IRI compensationIri = gateway.findCompensation(actionIri);
        if (compensationIri != null) {
            log.info("Triggering compensation {} for action {}", compensationIri, actionIri);
            String compId = utils.generateCompensationId();
            ActionData originalAction = gateway.fetchActionStructure(actionIri);
            if (originalAction != null) {
                gateway.createActionWorkflow(originalAction.target(), compensationIri, compId);
                log.debug("Compensation workflow {} created in State_Initial", compId);
            }
        }
    }

    /**
     * Petri Net Join Logic:
     * Verifies if all children in the decomposition are SUCCEEDED.
     */
    private void checkParentCompletion(IRI parentIri) {
        List<IRI> children = gateway.findChildren(parentIri);
        boolean allSucceeded = true;

        Map<IRI, WorkflowState> childStates = gateway.getStates(children);
        for (IRI child : children) {
            WorkflowState childState = childStates.get(child);
            if (childState != WorkflowState.SUCCEEDED) {
                log.debug("Parent {} not finished: child {} is in state {}", parentIri, child, childState);
                allSucceeded = false;
                break;
            }
        }

        if (allSucceeded) {
            log.debug("All children finished. Marking parent workflow {} as SUCCEEDED", parentIri);
            boolean transitioned = stateRepository.transition(parentIri, WorkflowState.PLANNED, WorkflowState.SUCCEEDED);

            if (transitioned) {
                // Recurse to parent's parent
                IRI grandParent = gateway.findParent(parentIri);
                if (grandParent != null) {
                    processSuccess(parentIri);
                }
            }
        }
    }
}
