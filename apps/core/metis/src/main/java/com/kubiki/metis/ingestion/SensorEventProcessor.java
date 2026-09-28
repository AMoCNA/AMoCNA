package com.kubiki.metis.ingestion;

import com.kubiki.daedalus.knowledge.SparqlClient;
import com.kubiki.metis.grpc.SensorEvent;
import com.kubiki.metis.ingestion.handler.SensorEventHandler;
import com.kubiki.metis.ingestion.model.HandlerResult;
import com.kubiki.metis.ingestion.model.ProcessResult;
import io.micrometer.core.annotation.Timed;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Dispatches a batch of {@link SensorEvent}s to the appropriate
 * {@link SensorEventHandler} and aggregates the results into a {@link ProcessResult}.
 *
 * <p>Spring auto-collects all {@code @Component}-annotated {@link SensorEventHandler}
 * implementations via constructor injection of {@code List<SensorEventHandler>}.
 */
@Component
public class SensorEventProcessor {

    private final List<SensorEventHandler> handlers;
    private final SparqlClient sparqlClient;

    public SensorEventProcessor(List<SensorEventHandler> handlers, SparqlClient sparqlClient) {
        this.handlers = handlers;
        this.sparqlClient = sparqlClient;
    }

    /**
     * Processes each event in the batch in order.
     */
    @Timed(value = "metis.ingestion.batch", description = "Time taken to process a batch of sensor events")
    public ProcessResult processBatch(List<SensorEvent> events, String correlationId) {
        int processedCount = 0;
        int failedCount = 0;
        List<String> failureMessages = new ArrayList<>();
        HandlerResult firstSuccess = null;
        boolean graphDbFailed = false;

        List<String> collectedSparql = new ArrayList<>();
        List<HandlerResult> successResults = new ArrayList<>();

        for (SensorEvent event : events) {
            SensorEventHandler handler = findHandler(event);

            if (handler == null) {
                failedCount++;
                failureMessages.add("No handler found for event type: " + event.getEventCase());
                continue;
            }

            HandlerResult result = handler.handle(event, correlationId);

            if (result.success()) {
                if (result.sparqlUpdate() != null) {
                    collectedSparql.add(result.sparqlUpdate());
                }
                successResults.add(result);
                if (firstSuccess == null) {
                    firstSuccess = result;
                }
            } else {
                failedCount++;
                if (result.failureReason() != null) {
                    failureMessages.add(result.failureReason());
                }
                if (result.graphDbFailed()) {
                    graphDbFailed = true;
                }
            }
        }

        if (!collectedSparql.isEmpty() && !graphDbFailed) {
            executeSparqlBatch(collectedSparql);
        }
        processedCount = successResults.size();

        return new ProcessResult(processedCount, failedCount, failureMessages, firstSuccess, graphDbFailed);
    }

    @Timed(value = "metis.knowledge.update", description = "Time taken to execute SPARQL batch update")
    private void executeSparqlBatch(List<String> collectedSparql) {
        sparqlClient.executeWithConnection(conn -> {
            for (String sparql : collectedSparql) {
                conn.prepareUpdate(sparql).execute();
            }
        });
    }

    /**
     * Returns the first handler that supports the event's case, or {@code null} if none does.
     */
    private SensorEventHandler findHandler(SensorEvent event) {
        SensorEvent.EventCase eventCase = event.getEventCase();
        for (SensorEventHandler handler : handlers) {
            if (handler.supports(eventCase)) {
                return handler;
            }
        }
        return null;
    }
}
