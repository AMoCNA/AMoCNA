package com.kubiki.palamedes.pipeline.pipes;

import com.kubiki.palamedes.dispatcher.DispatcherService;
import com.kubiki.palamedes.knowledge.StateRepository;
import com.kubiki.palamedes.model.ActionData;
import com.kubiki.palamedes.model.ActionMessage;
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

import java.util.Map;

@Order(4)
@Component
@RequiredArgsConstructor
public class ActionDispatcherPipe implements MapePipe {
    private static final Logger log = LoggerFactory.getLogger(ActionDispatcherPipe.class);
    private final StateRepository stateRepository;
    private final DispatcherService dispatcherService;
    private final PlannerService plannerService;
    private final WorkflowStateMapper mapper;

    @Override
    public boolean process(WorkflowContext context) {
        if (!mapper.getFragment(WorkflowState.VALIDATED).equals(context.metadata().get("currentState"))) {
            return true;
        }

        log.info("Evaluating dispatch strategies for action {}", context.actionId());

        return switch (context.actionData()) {
            case ActionData.SimpleAction simpleAction -> {
                Map<String, String> hydrationData = Map.of("resourceName", (String) context.metadata().get("resourceName"));
                ActionMessage message = plannerService.buildActionMessage(simpleAction, hydrationData);

                dispatcherService.dispatch(message);

                yield !stateRepository.transition(context.actionId(), WorkflowState.VALIDATED, WorkflowState.IN_PROGRESS);
            }
            case ActionData.ComplexWorkflow _ -> {
                log.debug("ComplexWorkflow node {} bypasses direct broker routing (HTN pipeline handles children)", context.actionId());
                yield true;
            }
        };
    }
}