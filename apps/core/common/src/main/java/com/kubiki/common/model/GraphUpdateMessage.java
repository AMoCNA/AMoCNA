package com.kubiki.common.model;

/**
 * Message published to the {@code amocna.graph.updates} RabbitMQ queue
 * after a successful knowledge-base write.
 *
 * <p>Serialized as JSON by Spring AMQP's Jackson converter.
 *
 * @param resourceIri   fully-qualified IRI of the affected resource
 * @param ontologyType  fully-qualified CNEEOnt class IRI
 * @param changeKind    one of: CREATED, UPDATED, STATE_CHANGED, DELETED
 * @param correlationId correlation identifier from the originating batch
 */
public record GraphUpdateMessage(
        String resourceIri,
        String ontologyType,
        String changeKind,
        String correlationId
) {
    /**
     * Compact canonical constructor for validation.
     */
    public GraphUpdateMessage {
        java.util.Objects.requireNonNull(resourceIri, "resourceIri must not be null");
        java.util.Objects.requireNonNull(ontologyType, "ontologyType must not be null");
        java.util.Objects.requireNonNull(changeKind, "changeKind must not be null");
        java.util.Objects.requireNonNull(correlationId, "correlationId must not be null");
    }
}
