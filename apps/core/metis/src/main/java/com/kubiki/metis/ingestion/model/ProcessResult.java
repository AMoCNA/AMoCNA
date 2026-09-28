package com.kubiki.metis.ingestion.model;

import java.util.List;

/**
 * Aggregated result of processing a batch of {@code SensorEvent}s by
 * {@link com.kubiki.metis.ingestion.SensorEventProcessor}.
 *
 * @param processedCount  number of events successfully written to the knowledge base
 * @param failedCount     number of events that failed validation or caused a write error
 * @param failureMessages human-readable descriptions of each failure, in order
 * @param firstSuccess    the first {@link HandlerResult} with {@code success == true},
 *                        or {@code null} if no event succeeded; used to trigger
 *                        Palamedes notification
 * @param graphDbFailed   {@code true} if at least one event failed due to a GraphDB
 *                        connectivity or server error (as opposed to a validation failure)
 */
public record ProcessResult(
        int processedCount,
        int failedCount,
        List<String> failureMessages,
        HandlerResult firstSuccess,   // nullable — used for Palamedes notification
        boolean graphDbFailed
) {
}
