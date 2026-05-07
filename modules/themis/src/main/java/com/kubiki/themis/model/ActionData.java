package com.kubiki.themis.model;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public sealed interface ActionData 
    permits ActionData.SimpleAction, ActionData.ComplexWorkflow {
    
    String id();
    String functionalIntent();

    record ConditionData(String id, String type, String policy) {}

    record SimpleAction(
        String id,                // GraphDB Individual IRI
        String functionalIntent,  // MoA Class (e.g. RestartAction)
        String protocol,          // REST, SHELL, gRPC
        String instruction,       // URL Template or script
        String targetIri,         // Resource individual
        Map<String, String> data, // Ground Truth parameters
        String method,            // HTTP Method, e.g. GET, POST
        String payload,           // Optional payload for POST/PUT
        List<ConditionData> preConditions,
        List<ConditionData> postConditions
    ) implements ActionData {}

    record ComplexWorkflow(
        String id,
        String functionalIntent,
        List<ActionData> steps,
        Map<String, ActionData> compensations
    ) implements ActionData {}

    /**
     * Immutable record for a specific execution instance.
     */
    record ExecutionTask(
        UUID executionId,
        ActionData action,
        String status // IN_PROGRESS, SUCCESS, FAILED
    ) {}
}
