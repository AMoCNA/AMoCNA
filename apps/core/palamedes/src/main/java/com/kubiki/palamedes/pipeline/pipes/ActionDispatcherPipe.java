package com.kubiki.palamedes.pipeline.pipes;

import com.kubiki.common.logging.LogLoopStep;
import com.kubiki.common.logging.LoopPhase;
import com.kubiki.common.model.ActionMessage;
import com.kubiki.palamedes.dispatcher.DispatcherService;
import com.kubiki.palamedes.knowledge.StateRepository;
import com.kubiki.palamedes.model.ActionData;
import com.kubiki.palamedes.model.WorkflowState;
import com.kubiki.palamedes.model.WorkflowStateMapper;
import com.kubiki.palamedes.pipeline.MapePipe;
import com.kubiki.palamedes.pipeline.WorkflowContext;
import com.kubiki.palamedes.planner.PlannerService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Order(4)
@Component
@RequiredArgsConstructor
public class ActionDispatcherPipe implements MapePipe {

    private static final Logger log = LoggerFactory.getLogger(ActionDispatcherPipe.class);

    /**
     * Pipeline control keys — not passed to instruction template hydration.
     */
    private static final Set<String> PIPELINE_METADATA_KEYS = Set.of("currentState");

    private final StateRepository stateRepository;
    private final DispatcherService dispatcherService;
    private final PlannerService plannerService;
    private final WorkflowStateMapper mapper;

    static Map<String, String> hydrationFromContext(WorkflowContext context) {
        Map<String, String> hydration = new HashMap<>();
        for (Map.Entry<String, Object> entry : context.metadata().entrySet()) {
            if (PIPELINE_METADATA_KEYS.contains(entry.getKey())) {
                continue;
            }
            if (entry.getValue() != null) {
                hydration.put(entry.getKey(), entry.getValue().toString());
            }
        }
        return hydration;
    }

    @Override
    @LogLoopStep(
            phase = LoopPhase.PLAN,
            step = "Action Dispatching Pipeline Trigger",
            actionId = "#context.actionId().stringValue()",
            resource = "#context.metadata().get('resourceName') != null ? #context.metadata().get('resourceName').toString() : null",
            details = "'currentState=' + #context.metadata().get('currentState')"
    )
    public boolean process(WorkflowContext context) {
        if (!mapper.getFragment(WorkflowState.VALIDATED).equals(context.metadata().get("currentState"))) {
            log.debug("ActionDispatcherPipe: Action is not in State_Validated, skipping dispatch");
            return true;
        }

        log.debug("ActionDispatcherPipe: Evaluating dispatch strategies");

        return switch (context.actionData()) {
            case ActionData.SimpleAction simpleAction -> {
                int delay = simpleAction.executionDelay();
                if (delay > 0) {
                    log.info("ActionDispatcherPipe: Delaying execution of action {} by {} seconds", simpleAction.id(), delay);
                    try {
                        Thread.sleep(delay * 1000L);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.error("ActionDispatcherPipe: Execution delay interrupted for action {}", simpleAction.id());
                        yield false;
                    }
                }

                Map<String, String> hydrationData = hydrationFromContext(context);
                log.debug("ActionDispatcherPipe: Building ActionMessage for SimpleAction");
                ActionMessage message = plannerService.buildActionMessage(simpleAction, hydrationData);

                log.debug("ActionDispatcherPipe: Dispatching ActionMessage via DispatcherService");
                dispatcherService.dispatch(message);

                log.debug("ActionDispatcherPipe: Transitioning action from State_Validated to State_InProgress");
                boolean success = stateRepository.transition(context.actionId(), WorkflowState.VALIDATED, WorkflowState.IN_PROGRESS);
                if (success) {
                    log.debug("ActionDispatcherPipe: Successfully transitioned action to State_InProgress");
                } else {
                    log.error("ActionDispatcherPipe: Failed to transition action to State_InProgress");
                }
                yield success;
            }
            case ActionData.ComplexWorkflow _ -> {
                log.debug("ActionDispatcherPipe: ComplexWorkflow node bypasses direct broker routing (HTN pipeline handles children)");
                yield true;
            }
        };
    }
}
