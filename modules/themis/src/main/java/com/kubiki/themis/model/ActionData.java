package com.kubiki.themis.model;

import org.springframework.http.HttpMethod;

import java.util.List;
import java.util.Map;

public sealed interface ActionData
        permits ActionData.SimpleAction, ActionData.ComplexWorkflow {

    String id();

    String functionalIntent();

    record ConditionData(String id, String type, String policy) {
    }

    record SimpleAction(
            String id,                // GraphDB Individual IRI
            String functionalIntent,  // MoA Class (e.g. RestartAction)
            Protocol protocol,
            String instruction,       // URL Template or script
            String targetIri,         // Resource individual
            Map<String, String> data, // Ground Truth parameters
            HttpMethod method,        // HTTP Method, e.g. GET, POST
            String payload,           // Optional payload for POST/PUT
            List<ConditionData> preConditions,
            List<ConditionData> postConditions
    ) implements ActionData {
    }

    record ComplexWorkflow(
            String id,
            String functionalIntent,
            List<ActionData> steps,
            Map<String, ActionData> compensations
    ) implements ActionData {
    }
}
