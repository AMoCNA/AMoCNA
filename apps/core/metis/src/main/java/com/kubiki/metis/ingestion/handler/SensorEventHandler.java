package com.kubiki.metis.ingestion.handler;

import com.kubiki.metis.grpc.SensorEvent;
import com.kubiki.metis.ingestion.model.HandlerResult;

/**
 * Strategy interface for handling a single {@link SensorEvent}.
 *
 * <p>Each concrete implementation is responsible for exactly one event type.
 * The {@link #supports(SensorEvent.EventCase)} method allows the
 * {@code SensorEventProcessor} to dispatch events to the correct handler
 * without coupling the processor to any specific event type.
 */
public interface SensorEventHandler {

    /**
     * Returns {@code true} if this handler is capable of processing events
     * of the given {@code eventCase}.
     *
     * @param eventCase the discriminator from the {@code oneof event} field
     * @return {@code true} iff this handler should be invoked for the given case
     */
    boolean supports(SensorEvent.EventCase eventCase);

    /**
     * Processes the given {@code event} and returns a result describing
     * whether the operation succeeded and, on success, the IRI and change kind
     * that should be forwarded to Palamedes.
     *
     * @param event         the sensor event to process; never {@code null}
     * @param correlationId the correlation ID from the enclosing {@code SensorBatch}
     * @return a {@link HandlerResult} — never {@code null}
     */
    HandlerResult handle(SensorEvent event, String correlationId);
}
