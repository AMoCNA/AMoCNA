package com.kubiki.metis.ingestion.handler;

import com.kubiki.metis.grpc.SensorEvent;
import com.kubiki.metis.grpc.StateChangedEvent;
import com.kubiki.metis.ingestion.model.HandlerResult;
import com.kubiki.metis.knowledge.KnowledgeBaseException;
import com.kubiki.metis.knowledge.KnowledgeBaseWriter;

import org.springframework.stereotype.Component;

/**
 * Handles {@link SensorEvent} instances whose {@code oneof event} is a
 * {@link StateChangedEvent}.
 *
 * <p>Validates that both {@code resource_iri} and {@code new_state_iri} are
 * non-blank, then delegates to {@link KnowledgeBaseWriter#changeState(StateChangedEvent)}.
 */
@Component
public class StateChangedHandler implements SensorEventHandler {

    private final KnowledgeBaseWriter writer;

    public StateChangedHandler(KnowledgeBaseWriter writer) {
        this.writer = writer;
    }

    @Override
    public boolean supports(SensorEvent.EventCase eventCase) {
        return SensorEvent.EventCase.STATE_CHANGED.equals(eventCase);
    }

    @Override
    public HandlerResult handle(SensorEvent event, String correlationId) {
        StateChangedEvent stateChanged = event.getStateChanged();
        String resourceIri = stateChanged.getResourceIri();
        String newStateIri = stateChanged.getNewStateIri();

        if (resourceIri == null || resourceIri.isBlank()) {
            return HandlerResult.failure("StateChangedHandler: resource_iri must not be blank");
        }
        if (newStateIri == null || newStateIri.isBlank()) {
            return HandlerResult.failure("StateChangedHandler: new_state_iri must not be blank");
        }

        try {
            String sparql = writer.changeState(stateChanged);
            return HandlerResult.success(resourceIri, newStateIri, HandlerResult.STATE_CHANGED, sparql);
        } catch (KnowledgeBaseException e) {
            return e.getCause() != null
                    ? HandlerResult.graphDbFailure(e.getMessage())
                    : HandlerResult.failure(e.getMessage());
        }
    }
}
