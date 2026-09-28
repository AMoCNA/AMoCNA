package com.kubiki.metis.ingestion.handler;

import com.kubiki.metis.grpc.SensorEvent;
import com.kubiki.metis.ingestion.model.HandlerResult;
import com.kubiki.metis.knowledge.KnowledgeBaseException;
import com.kubiki.metis.knowledge.KnowledgeBaseWriter;

import org.springframework.stereotype.Component;

/**
 * Handles {@link SensorEvent.EventCase#ENTITY_DISCOVERED} events by inserting
 * the described entity into the knowledge base.
 */
@Component
public class EntityDiscoveredHandler implements SensorEventHandler {

    private final KnowledgeBaseWriter writer;

    public EntityDiscoveredHandler(KnowledgeBaseWriter writer) {
        this.writer = writer;
    }

    @Override
    public boolean supports(SensorEvent.EventCase eventCase) {
        return SensorEvent.EventCase.ENTITY_DISCOVERED.equals(eventCase);
    }

    @Override
    public HandlerResult handle(SensorEvent event, String correlationId) {
        String resourceIri = event.getEntityDiscovered().getResourceIri();
        String ontologyType = event.getEntityDiscovered().getOntologyType();

        if (resourceIri == null || resourceIri.isBlank()) {
            return HandlerResult.failure("EntityDiscoveredHandler: resource_iri must not be blank");
        }

        try {
            String sparql = writer.insertEntity(event.getEntityDiscovered());
            return HandlerResult.success(resourceIri, ontologyType, HandlerResult.CREATED, sparql);
        } catch (KnowledgeBaseException e) {
            return e.getCause() != null
                    ? HandlerResult.graphDbFailure(e.getMessage())
                    : HandlerResult.failure(e.getMessage());
        }
    }
}
