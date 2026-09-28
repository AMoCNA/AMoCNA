package com.kubiki.metis.ingestion.handler;

import com.kubiki.metis.grpc.RelationshipAssertedEvent;
import com.kubiki.metis.grpc.SensorEvent;
import com.kubiki.metis.ingestion.model.HandlerResult;
import com.kubiki.metis.knowledge.KnowledgeBaseException;
import com.kubiki.metis.knowledge.KnowledgeBaseWriter;

import org.springframework.stereotype.Component;

/**
 * Handles {@link SensorEvent.EventCase#RELATIONSHIP_ASSERTED} events by
 * delegating to {@link KnowledgeBaseWriter#assertRelationship(RelationshipAssertedEvent)}.
 */
@Component
public class RelationshipAssertedHandler implements SensorEventHandler {

    private final KnowledgeBaseWriter writer;

    public RelationshipAssertedHandler(KnowledgeBaseWriter writer) {
        this.writer = writer;
    }

    @Override
    public boolean supports(SensorEvent.EventCase eventCase) {
        return SensorEvent.EventCase.RELATIONSHIP_ASSERTED.equals(eventCase);
    }

    @Override
    public HandlerResult handle(SensorEvent event, String correlationId) {
        RelationshipAssertedEvent rel = event.getRelationshipAsserted();

        String subjectIri = rel.getSubjectIri();
        String objectIri = rel.getObjectIri();
        String predicate = rel.getPredicate();

        if (subjectIri == null || subjectIri.isBlank()) {
            return HandlerResult.failure("subject_iri must not be blank");
        }
        if (objectIri == null || objectIri.isBlank()) {
            return HandlerResult.failure("object_iri must not be blank");
        }

        try {
            String sparql = writer.assertRelationship(rel);
            return HandlerResult.success(subjectIri, predicate, HandlerResult.UPDATED, sparql);
        } catch (KnowledgeBaseException e) {
            return e.getCause() != null
                    ? HandlerResult.graphDbFailure(e.getMessage())
                    : HandlerResult.failure(e.getMessage());
        }
    }
}
