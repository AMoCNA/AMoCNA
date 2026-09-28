package com.kubiki.palamedes.pipeline.pipes.rules;

import com.kubiki.palamedes.config.PalamedesProperties;
import com.kubiki.palamedes.knowledge.GraphDBGateway;
import com.kubiki.palamedes.knowledge.StateRepository;
import com.kubiki.palamedes.model.ActionData;
import com.kubiki.palamedes.model.ActiveActionSummary;
import com.kubiki.palamedes.model.WorkflowState;
import com.kubiki.palamedes.model.WorkflowStateMapper;
import com.kubiki.palamedes.pipeline.WorkflowContext;
import org.eclipse.rdf4j.model.IRI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OpposingIntentsSpecification implements Specification<SchedulingTarget> {
    private static final Logger log = LoggerFactory.getLogger(OpposingIntentsSpecification.class);

    private static final String STATUS_FAILED_CONFLICT = "FAILED_CONFLICT";

    private final StateRepository stateRepository;
    private final GraphDBGateway graphDBGateway;
    private final PalamedesProperties palamedesProperties;
    private final WorkflowStateMapper stateMapper;

    @Autowired
    public OpposingIntentsSpecification(StateRepository stateRepository,
                                        GraphDBGateway graphDBGateway,
                                        PalamedesProperties palamedesProperties,
                                        WorkflowStateMapper stateMapper) {
        this.stateRepository = stateRepository;
        this.graphDBGateway = graphDBGateway;
        this.palamedesProperties = palamedesProperties;
        this.stateMapper = stateMapper;
    }

    @Override
    public boolean isSatisfiedBy(SchedulingTarget target) {
        WorkflowContext context = target.context();
        SchedulingState state = target.state();

        IRI currentActionId = context.actionId();
        ActionData currentAction = context.actionData();
        IRI currentTarget = currentAction.target();

        if (currentTarget == null) {
            return true;
        }

        // Find opposing actions targeting the same resource
        List<ActiveActionSummary> conflicting = state.activeSummaries().stream()
                .filter(active -> !active.actionIri().equals(currentActionId))
                .filter(active -> currentTarget.equals(active.resourceIri()))
                .filter(active -> {
                    ActionData other = state.structures().get(active.actionIri());
                    return other != null && areOpposingIntents(currentAction.functionalIntent(), other.functionalIntent());
                })
                .toList();

        // If any opposing action is already IN_PROGRESS, block current execution
        boolean inProgressConflict = conflicting.stream()
                .anyMatch(active -> {
                    try {
                        WorkflowState ws = stateMapper.fromFragment(active.stateFragment());
                        return ws == WorkflowState.IN_PROGRESS;
                    } catch (IllegalArgumentException e) {
                        log.warn("OpposingIntentsSpecification: Unknown state fragment: {}", active.stateFragment());
                        return false;
                    }
                });

        if (inProgressConflict) {
            log.info("OptimizationPipe: Opposing intent targeting {} is already IN_PROGRESS. Postponing action {}",
                    currentTarget, currentActionId);
            return false;
        }

        // Handle opposing actions that are in PLANNED / VALIDATED state (pre-emption)
        for (ActiveActionSummary active : conflicting) {
            WorkflowState otherState;
            try {
                otherState = stateMapper.fromFragment(active.stateFragment());
            } catch (IllegalArgumentException e) {
                log.warn("OpposingIntentsSpecification: Unknown state fragment: {}", active.stateFragment());
                continue;
            }

            if (otherState == WorkflowState.PLANNED || otherState == WorkflowState.VALIDATED) {
                ActionData otherAction = state.structures().get(active.actionIri());
                if (otherAction != null) {
                    if (currentAction.priority() < otherAction.priority()) {
                        log.info("OptimizationPipe: Opposing intent (action {}) has higher priority ({}) than current action priority ({}). Postponing.",
                                active.actionIri(), otherAction.priority(), currentAction.priority());
                        return false;
                    } else {
                        // Current has higher or equal priority. Cancel the other planned action to pre-empt it!
                        log.info("OptimizationPipe: Current action {} has higher/equal priority than opposing planned action {}. Cancelling it.",
                                currentActionId, active.actionIri());
                        stateRepository.transition(active.actionIri(), otherState, WorkflowState.FAILED);
                        graphDBGateway.updateExecutionStatus(active.actionIri(), STATUS_FAILED_CONFLICT);
                    }
                }
            }
        }

        return true;
    }

    private boolean areOpposingIntents(IRI intent1, IRI intent2) {
        if (intent1 == null || intent2 == null) return false;
        String name1 = intent1.getLocalName();
        String name2 = intent2.getLocalName();

        List<PalamedesProperties.OpposingPair> pairs = palamedesProperties.scheduler().opposingIntents();
        if (pairs == null) {
            pairs = PalamedesProperties.DEFAULT_OPPOSING_INTENTS;
        }

        for (PalamedesProperties.OpposingPair pair : pairs) {
            boolean match1 = name1.toLowerCase().contains(pair.first().toLowerCase())
                    && name2.toLowerCase().contains(pair.second().toLowerCase());
            boolean match2 = name1.toLowerCase().contains(pair.second().toLowerCase())
                    && name2.toLowerCase().contains(pair.first().toLowerCase());
            if (match1 || match2) {
                return true;
            }
        }

        return false;
    }
}

