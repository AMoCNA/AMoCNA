package com.kubiki.palamedes.saga;

import com.kubiki.palamedes.model.WorkflowState;
import java.util.Map;
import java.util.Optional;

public class SagaStateMachine {
    public enum Event {
        PLAN,
        VALIDATE,
        DISPATCH,
        EXECUTE_SUCCESS,
        EXECUTE_FAILURE,
        COMPENSATE
    }

    private static final Map<WorkflowState, Map<Event, WorkflowState>> TRANSITION_TABLE = Map.of(
        WorkflowState.INITIAL, Map.of(Event.PLAN, WorkflowState.PLANNED),
        WorkflowState.PLANNED, Map.of(
            Event.VALIDATE, WorkflowState.VALIDATED,
            Event.COMPENSATE, WorkflowState.COMPENSATING,
            Event.EXECUTE_SUCCESS, WorkflowState.SUCCEEDED
        ),
        WorkflowState.VALIDATED, Map.of(Event.DISPATCH, WorkflowState.IN_PROGRESS),
        WorkflowState.IN_PROGRESS, Map.of(
            Event.EXECUTE_SUCCESS, WorkflowState.SUCCEEDED,
            Event.EXECUTE_FAILURE, WorkflowState.FAILED,
            Event.COMPENSATE, WorkflowState.COMPENSATING
        ),
        WorkflowState.COMPENSATING, Map.of(Event.EXECUTE_FAILURE, WorkflowState.FAILED)
    );

    public static Optional<WorkflowState> getNextState(WorkflowState current, Event event) {
        return Optional.ofNullable(TRANSITION_TABLE.get(current))
                .map(m -> m.get(event));
    }
}
