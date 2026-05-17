package com.kubiki.palamedes.model;

import com.kubiki.palamedes.config.PalamedesProperties;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.stream.Collectors;

import static com.kubiki.palamedes.knowledge.KnowledgeConstants.*;

@Component
public class WorkflowStateMapper {

    private final Map<WorkflowState, String> stateToString;
    private final Map<String, WorkflowState> stringToState;

    public WorkflowStateMapper(PalamedesProperties properties) {
        var states = properties.ontology().states();

        this.stateToString = Map.of(
                WorkflowState.INITIAL, states.getOrDefault(PROPERTIES_INITIAL_STATE_NAME, DEFAULT_STATE_INITIAL),
                WorkflowState.PLANNED, states.getOrDefault(PROPERTIES_PLANNED_STATE_NAME, DEFAULT_STATE_PLANNED),
                WorkflowState.VALIDATED, states.getOrDefault(PROPERTIES_VALIDATED_STATE_NAME, DEFAULT_STATE_VALIDATED),
                WorkflowState.IN_PROGRESS, states.getOrDefault(PROPERTIES_IN_PROGRESS_STATE_NAME, DEFAULT_STATE_IN_PROGRESS),
                WorkflowState.SUCCEEDED, states.getOrDefault(PROPERTIES_SUCCEEDED_STATE_NAME, DEFAULT_STATE_SUCCEEDED),
                WorkflowState.FAILED, states.getOrDefault(PROPERTIES_FAILED_STATE_NAME, DEFAULT_STATE_FAILED),
                WorkflowState.COMPENSATING, states.getOrDefault(PROPERTIES_COMPENSATING_STATE_NAME, DEFAULT_STATE_COMPENSATING)
        );

        this.stringToState = stateToString.entrySet().stream()
                .collect(Collectors.toMap(
                        e -> e.getValue().toLowerCase(),
                        Map.Entry::getKey
                ));
    }

    public String getFragment(WorkflowState state) {
        return stateToString.get(state);
    }

    public WorkflowState fromFragment(String fragment) {
        WorkflowState state = stringToState.get(fragment.toLowerCase());
        if (state == null) {
            throw new IllegalArgumentException("Unknown state fragment: " + fragment);
        }
        return state;
    }
}