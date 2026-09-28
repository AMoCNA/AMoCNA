package com.kubiki.metis.ingestion.handler;

import com.kubiki.metis.grpc.SensorEvent;
import com.kubiki.metis.ingestion.model.HandlerResult;
import com.kubiki.metis.knowledge.KnowledgeBaseException;
import com.kubiki.metis.knowledge.KnowledgeBaseWriter;

import org.springframework.stereotype.Component;

/**
 * Handles {@link com.kubiki.metis.grpc.EntityDeletedEvent} sensor events.
 *
 * <p>Validates that {@code resource_iri} is non-blank, then delegates to
 * {@link KnowledgeBaseWriter#deleteEntity} to remove all triples for the entity
 * from the knowledge base. On success, returns a {@link HandlerResult} with
 * {@link ChangeKind#DELETED}; on {@link KnowledgeBaseException}, returns a
 * failure result.
 */
@Component
public class EntityDeletedHandler implements SensorEventHandler {

    private final KnowledgeBaseWriter writer;

    public EntityDeletedHandler(KnowledgeBaseWriter writer) {
        this.writer = writer;
    }

    @Override
    public boolean supports(SensorEvent.EventCase eventCase) {
        return SensorEvent.EventCase.ENTITY_DELETED.equals(eventCase);
    }

    @Override
    public HandlerResult handle(SensorEvent event, String correlationId) {
        var entityDeleted = event.getEntityDeleted();
        String resourceIri = entityDeleted.getResourceIri();
        String ontologyType = entityDeleted.getOntologyType();

        if (resourceIri == null || resourceIri.isBlank()) {
            return HandlerResult.failure("EntityDeletedHandler: resource_iri must not be blank");
        }

        try {
            String sparql = writer.deleteEntity(entityDeleted);
            return HandlerResult.success(resourceIri, ontologyType, HandlerResult.DELETED, sparql);
        } catch (KnowledgeBaseException e) {
            return e.getCause() != null
                    ? HandlerResult.graphDbFailure(e.getMessage())
                    : HandlerResult.failure(e.getMessage());
        }
    }
}
