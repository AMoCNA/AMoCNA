package com.kubiki.palamedes.model;

import lombok.Builder;
import org.eclipse.rdf4j.model.IRI;
import org.springframework.http.HttpMethod;

import java.util.List;
import java.util.Map;

public sealed interface ActionData
        permits ActionData.SimpleAction, ActionData.ComplexWorkflow {

    IRI id();

    IRI functionalIntent();

    IRI layerBoundary();

    float executionCost();

    IRI target();

    List<Condition> preConditions();

    List<Condition> postConditions();

    record Condition(IRI id, IRI type, String policy) {
    }

    @Builder
    record SimpleAction(
            IRI id,
            IRI functionalIntent,
            IRI layerBoundary,
            float executionCost,
            Protocol protocol,
            String instruction,
            IRI target,
            Map<String, String> data,
            HttpMethod method,
            String payload,
            List<Condition> preConditions,
            List<Condition> postConditions,
            int expectedStatusCode,
            String authMechanism,
            int timeoutSeconds,
            boolean isIdempotent,
            int maxRetries
    ) implements ActionData {
    }

    @Builder
    record ComplexWorkflow(
            IRI id,
            IRI functionalIntent,
            IRI layerBoundary,
            float executionCost,
            IRI target,
            List<ActionData> steps,
            Map<IRI, ActionData> compensations
    ) implements ActionData {
        @Override
        public List<Condition> preConditions() {
            return List.of();
        }

        @Override
        public List<Condition> postConditions() {
            return List.of();
        }
    }
}
