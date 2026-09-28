package com.kubiki.palamedes.model;

import com.kubiki.common.model.Protocol;
import com.kubiki.palamedes.knowledge.RdfBinding;
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

    int idempotencyWindowSeconds();

    int priority();

    int executionDelay();

    String idempotencyKey();

    List<Condition> preConditions();

    List<Condition> postConditions();

    record Condition(IRI id, IRI type, String policy) {
    }

    @Builder
    record SimpleAction(
            IRI id,
            @RdfBinding("functionalIntent") IRI functionalIntent,
            @RdfBinding("layerBoundary") IRI layerBoundary,
            @RdfBinding(value = "costValue", defaultValue = "1.0") float executionCost,
            @RdfBinding("protocol") Protocol protocol,
            @RdfBinding("instruction") String instruction,
            @RdfBinding("target") IRI target,
            Map<String, String> data,
            @RdfBinding("method") HttpMethod method,
            @RdfBinding("payload") String payload,
            List<Condition> preConditions,
            List<Condition> postConditions,
            @RdfBinding("expectedStatusCode") int expectedStatusCode,
            @RdfBinding("authMechanism") String authMechanism,
            @RdfBinding(value = "timeoutSeconds", defaultValue = "30") int timeoutSeconds,
            @RdfBinding(value = "isIdempotent", defaultValue = "true") boolean isIdempotent,
            @RdfBinding(value = "maxRetries", defaultValue = "3") int maxRetries,
            @RdfBinding(value = "idempotencyWindowSeconds", defaultValue = "30") int idempotencyWindowSeconds,
            @RdfBinding(value = "priority", defaultValue = "5") int priority,
            @RdfBinding(value = "executionDelay", defaultValue = "0") int executionDelay,
            @RdfBinding("idempotencyKey") String idempotencyKey
    ) implements ActionData {
    }

    @Builder
    record ComplexWorkflow(
            IRI id,
            @RdfBinding("functionalIntent") IRI functionalIntent,
            @RdfBinding("layerBoundary") IRI layerBoundary,
            @RdfBinding(value = "costValue", defaultValue = "1.0") float executionCost,
            @RdfBinding("target") IRI target,
            @RdfBinding(value = "idempotencyWindowSeconds", defaultValue = "30") int idempotencyWindowSeconds,
            @RdfBinding(value = "priority", defaultValue = "5") int priority,
            @RdfBinding(value = "executionDelay", defaultValue = "0") int executionDelay,
            @RdfBinding("idempotencyKey") String idempotencyKey,
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

