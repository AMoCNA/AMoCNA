package com.kubiki.metis.ingestion.model;

/**
 * Result returned by a {@link com.kubiki.metis.ingestion.handler.SensorEventHandler}
 * after processing a single {@code SensorEvent}.
 *
 * <p>Use the static factories {@link #success}, {@link #failure}, and
 * {@link #graphDbFailure} rather than the canonical record constructor.
 */
public record HandlerResult(
        boolean success,
        String resourceIri,
        String ontologyType,
        String changeKind,
        String failureReason,
        boolean graphDbFailed,
        String sparqlUpdate
) {

    /**
     * Change kind constants — matches the values Palamedes expects.
     */
    public static final String CREATED = "CREATED";
    public static final String UPDATED = "UPDATED";
    public static final String STATE_CHANGED = "STATE_CHANGED";
    public static final String DELETED = "DELETED";

    /**
     * Creates a successful result carrying the IRI, ontology type, and change kind
     * that will be forwarded to Palamedes via RabbitMQ.
     */
    public static HandlerResult success(String resourceIri, String ontologyType, String changeKind, String sparqlUpdate) {
        return new HandlerResult(true, resourceIri, ontologyType, changeKind, null, false, sparqlUpdate);
    }

    /**
     * Creates a validation failure result. All IRI / type / kind fields are {@code null}.
     */
    public static HandlerResult failure(String reason) {
        return new HandlerResult(false, null, null, null, reason, false, null);
    }

    /**
     * Creates a GraphDB failure result indicating the knowledge base was unavailable.
     */
    public static HandlerResult graphDbFailure(String reason) {
        return new HandlerResult(false, null, null, null, reason, true, null);
    }
}
