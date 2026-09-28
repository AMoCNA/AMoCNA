package com.kubiki.palamedes.pipeline.pipes;

import com.kubiki.common.logging.LogLoopStep;
import com.kubiki.common.logging.LoopPhase;
import com.kubiki.palamedes.condition.ConditionFactory;
import com.kubiki.palamedes.condition.ConditionStrategy;
import com.kubiki.palamedes.knowledge.GraphDBGateway;
import com.kubiki.palamedes.knowledge.StateRepository;
import com.kubiki.palamedes.model.ActionData;
import com.kubiki.palamedes.model.WorkflowState;
import com.kubiki.palamedes.model.WorkflowStateMapper;
import com.kubiki.palamedes.pipeline.MapePipe;
import com.kubiki.palamedes.pipeline.WorkflowContext;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * SafetyValidatorPipe (MAPE-Plan/Execute):
 * Verifies PreConditions before an action is transitioned to VALIDATED.
 */
@Order(3)
@Component
@RequiredArgsConstructor
public class SafetyValidatorPipe implements MapePipe {
    private static final Logger log = LoggerFactory.getLogger(SafetyValidatorPipe.class);
    private final StateRepository stateRepository;
    private final ConditionFactory conditionFactory;
    private final GraphDBGateway graphDBGateway;
    private final WorkflowStateMapper mapper;


    @Override
    @LogLoopStep(
            phase = LoopPhase.PLAN,
            step = "Safety Validation",
            actionId = "#context.actionId().stringValue()",
            resource = "#context.metadata().get('resourceName') != null ? #context.metadata().get('resourceName').toString() : null",
            details = "'currentState=' + #context.metadata().get('currentState') + ', idempotencyOpen=' + #context.metadata().get('idempotencyOpen')"
    )
    public boolean process(WorkflowContext context) {
        if (!mapper.getFragment(WorkflowState.PLANNED).equals(context.metadata().get("currentState"))) {
            log.debug("SafetyValidatorPipe: Action is not in State_Planned, skipping safety validation");
            return true;
        }

        log.debug("SafetyValidatorPipe: Starting Safety Validation");

        // 1. Idempotency Gate (Temporal safety)
        Object val = context.metadata().get("idempotencyOpen");
        boolean isOpen = val instanceof Boolean ? (Boolean) val : true;
        if (!isOpen) {
            log.info("SafetyValidatorPipe: Action is blocked by Idempotency Window - transitioning to FAILED");
            boolean transitioned = stateRepository.transition(context.actionId(), WorkflowState.PLANNED, WorkflowState.FAILED);
            if (!transitioned) {
                log.error("SafetyValidatorPipe: Failed to transition action to FAILED after idempotency block");
            }
            return false;
        }
        log.debug("SafetyValidatorPipe: Idempotency gate check passed");

        // 2. Pre-condition Gate (Semantic safety)
        if (!evaluatePreConditions(context)) {
            log.warn("SafetyValidatorPipe: Pre-condition gate FAILED");
            return false;
        }
        log.debug("SafetyValidatorPipe: Pre-condition gate check passed");

        log.debug("SafetyValidatorPipe: Transitioning action from State_Planned to State_Validated");
        boolean success = stateRepository.transition(context.actionId(), WorkflowState.PLANNED, WorkflowState.VALIDATED);
        if (success) {
            context.metadata().put("currentState", mapper.getFragment(WorkflowState.VALIDATED));
            log.debug("SafetyValidatorPipe: Successfully transitioned action to State_Validated");
        } else {
            log.error("SafetyValidatorPipe: Failed to transition action to State_Validated");
        }
        return success;
    }

    private boolean evaluatePreConditions(WorkflowContext context) {
        log.debug("SafetyValidatorPipe: Evaluating {} pre-conditions",
                context.actionData().preConditions().size());
        for (ActionData.Condition cond : context.actionData().preConditions()) {
            Optional<ConditionStrategy> strategy = conditionFactory.getStrategy(cond.type());
            if (strategy.isPresent()) {
                try {
                    log.debug("SafetyValidatorPipe: Evaluating pre-condition {} of type {}", cond.id(), cond.type());
                    if (!strategy.get().evaluate(cond)) {
                        log.warn("SafetyValidatorPipe: Pre-condition {} NOT MET", cond.id());
                        return false;
                    }
                } catch (Exception e) {
                    log.error("SafetyValidatorPipe: Error evaluating pre-condition {}: {}", cond.id(), e.getMessage());
                    return false;
                }
            } else {
                log.error("SafetyValidatorPipe: Unknown condition type: {}", cond.type());
                return false;
            }
        }
        return true;
    }
}
