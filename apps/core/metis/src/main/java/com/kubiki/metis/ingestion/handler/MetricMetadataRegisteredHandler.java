package com.kubiki.metis.ingestion.handler;

import com.kubiki.metis.grpc.MetricMetadataRegisteredEvent;
import com.kubiki.metis.grpc.SensorEvent;
import com.kubiki.metis.ingestion.model.HandlerResult;
import com.kubiki.metis.knowledge.CneeOntology;
import com.kubiki.metis.knowledge.KnowledgeBaseException;
import com.kubiki.metis.knowledge.KnowledgeBaseWriter;
import com.kubiki.metis.sensor.IriFactory;

import org.springframework.stereotype.Component;

/**
 * Handles {@link SensorEvent.EventCase#METRIC_METADATA_REGISTERED} events by
 * registering metric metadata for the described resource in the knowledge base.
 */
@Component
public class MetricMetadataRegisteredHandler implements SensorEventHandler {

    private final KnowledgeBaseWriter writer;
    private final IriFactory iriFactory;

    public MetricMetadataRegisteredHandler(KnowledgeBaseWriter writer, IriFactory iriFactory) {
        this.writer = writer;
        this.iriFactory = iriFactory;
    }

    @Override
    public boolean supports(SensorEvent.EventCase eventCase) {
        return SensorEvent.EventCase.METRIC_METADATA_REGISTERED.equals(eventCase);
    }

    @Override
    public HandlerResult handle(SensorEvent event, String correlationId) {
        MetricMetadataRegisteredEvent metricEvent = event.getMetricMetadataRegistered();
        String resourceIri = metricEvent.getResourceIri();
        String endpointUrl = metricEvent.getEndpointUrl();

        if (resourceIri == null || resourceIri.isBlank()) {
            return HandlerResult.failure("MetricMetadataRegisteredHandler: resource_iri must not be blank");
        }
        if (endpointUrl == null || endpointUrl.isBlank()) {
            return HandlerResult.failure("MetricMetadataRegisteredHandler: endpoint_url must not be blank");
        }

        try {
            String sparql = writer.registerMetricMetadata(metricEvent);
            String metricTypeIri = iriFactory.typeIri(CneeOntology.CLASS_METRIC);
            return HandlerResult.success(resourceIri, metricTypeIri, HandlerResult.UPDATED, sparql);
        } catch (KnowledgeBaseException e) {
            return e.getCause() != null
                    ? HandlerResult.graphDbFailure(e.getMessage())
                    : HandlerResult.failure(e.getMessage());
        }
    }
}
