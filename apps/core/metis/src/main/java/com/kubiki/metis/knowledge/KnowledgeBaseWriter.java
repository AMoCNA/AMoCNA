package com.kubiki.metis.knowledge;

import com.kubiki.metis.grpc.*;
import com.kubiki.metis.sensor.IriFactory;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
@Slf4j
public class KnowledgeBaseWriter {

    private final MetisDaedalusRepository repository;
    private final String cneeNamespace;
    private final MeterRegistry meterRegistry;

    public KnowledgeBaseWriter(MetisDaedalusRepository repository, IriFactory iriFactory, MeterRegistry meterRegistry) {
        this.repository = repository;
        this.cneeNamespace = iriFactory.getCneeNamespace();
        this.meterRegistry = meterRegistry;
    }

    @Timed(value = "metis.writer.op", extraTags = {"operation", "insertEntity"}, description = "Time taken to insert entity")
    public String insertEntity(EntityDiscoveredEvent event) throws KnowledgeBaseException {
        String resourceIri = event.getResourceIri();
        String ontologyType = event.getOntologyType();
        String resourceId = event.getResourceId();
        String resourceName = event.getResourceName();

        if (isValidCneeIri(ontologyType)) {
            throw new KnowledgeBaseException(
                    "Rejected: ontology_type '" + ontologyType +
                            "' does not start with CNEEOnt namespace '" + cneeNamespace + "'");
        }

        StringBuilder extraProperties = new StringBuilder();
        for (Map.Entry<String, String> entry : event.getPropertiesMap().entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            if (key == null || key.isEmpty() || value == null) continue;
            if (!key.startsWith(cneeNamespace)) continue;

            String localName = key.substring(cneeNamespace.length());
            extraProperties.append(" ;\n    cnee:").append(localName).append(" \"")
                    .append(escapeLiteral(value)).append("\"^^xsd:string");
        }
        String triplesSuffix = extraProperties.isEmpty() ? " ." : extraProperties + " .";

        try {
            return repository.insertEntity(
                    resourceIri,
                    ontologyType,
                    escapeLiteral(resourceId),
                    escapeLiteral(resourceName),
                    triplesSuffix);
        } catch (Exception e) {
            throw new KnowledgeBaseException("insertEntity failed: " + e.getMessage(), e);
        }
    }

    @Timed(value = "metis.writer.op", extraTags = {"operation", "assertRelationship"}, description = "Time taken to assert relationship")
    public String assertRelationship(RelationshipAssertedEvent event) throws KnowledgeBaseException {
        String subjectIri = event.getSubjectIri();
        String predicate = event.getPredicate();
        String objectIri = event.getObjectIri();

        validateIri(subjectIri, "subject_iri");
        validateIri(objectIri, "object_iri");
        if (isValidCneeIri(predicate)) {
            throw new KnowledgeBaseException("predicate does not start with CNEEOnt namespace: " + predicate);
        }

        String inverseLocalName = inverseLocalName(predicate);
        String predicateLocalName = predicate.substring(cneeNamespace.length());

        try {
            if (inverseLocalName != null) {
                return repository.assertRelationshipPair(subjectIri, predicateLocalName, objectIri, inverseLocalName);
            } else {
                return repository.assertRelationship(subjectIri, predicateLocalName, objectIri);
            }
        } catch (Exception e) {
            throw new KnowledgeBaseException("assertRelationship failed: " + e.getMessage(), e);
        }
    }

    @Timed(value = "metis.writer.op", extraTags = {"operation", "changeState"}, description = "Time taken to change state")
    public String changeState(StateChangedEvent event) throws KnowledgeBaseException {
        try {
            String resourceIri = event.getResourceIri();
            String newStateIri = event.getNewStateIri();

            if (resourceIri == null || resourceIri.isBlank()) {
                throw new KnowledgeBaseException("changeState: resource_iri must not be blank");
            }
            if (newStateIri == null || newStateIri.isBlank()) {
                throw new KnowledgeBaseException("changeState: new_state_iri must not be blank");
            }
            if (isValidCneeIri(newStateIri)) {
                throw new KnowledgeBaseException(
                        "changeState: new_state_iri must start with CNEEOnt namespace, got: " + newStateIri);
            }

            try {
                return repository.changeState(resourceIri, newStateIri);
            } catch (Exception e) {
                throw new KnowledgeBaseException("changeState failed: " + e.getMessage(), e);
            }
        } catch (KnowledgeBaseException e) {
            throw e;
        }
    }

    @Timed(value = "metis.writer.op", extraTags = {"operation", "deleteEntity"}, description = "Time taken to delete entity")
    public String deleteEntity(EntityDeletedEvent event) throws KnowledgeBaseException {
        String resourceIri = event.getResourceIri();
        if (resourceIri == null || resourceIri.isBlank()) {
            throw new KnowledgeBaseException("deleteEntity: resource_iri must not be blank");
        }

        try {
            return repository.deleteEntity(resourceIri);
        } catch (Exception e) {
            throw new KnowledgeBaseException("deleteEntity failed: " + e.getMessage(), e);
        }
    }

    @Timed(value = "metis.writer.op", extraTags = {"operation", "registerMetricMetadata"}, description = "Time taken to register metric metadata")
    public String registerMetricMetadata(MetricMetadataRegisteredEvent event) throws KnowledgeBaseException {
        String resourceIri = event.getResourceIri();
        String endpointUrl = event.getEndpointUrl();
        String metricName = event.getMetricName();

        if (resourceIri == null || resourceIri.isBlank()) {
            throw new KnowledgeBaseException("registerMetricMetadata: resource_iri must not be blank");
        }

        try {
            java.net.URI uri = new java.net.URI(endpointUrl);
            if (!uri.isAbsolute()) {
                throw new KnowledgeBaseException(
                        "registerMetricMetadata: endpoint_url is not an absolute URI: " + endpointUrl);
            }
        } catch (Exception e) {
            throw new KnowledgeBaseException(
                    "registerMetricMetadata: endpoint_url is not a valid URI: " + endpointUrl, e);
        }

        String resourceLocalName = localNameOf(resourceIri);
        if (resourceLocalName == null) {
            throw new KnowledgeBaseException(
                    "registerMetricMetadata: cannot derive local name from resource_iri: " + resourceIri);
        }
        String metricIri = cneeNamespace + encodeFragment(resourceLocalName) + "_" + encodeFragment(metricName);

        try {
            return repository.registerMetricMetadata(resourceIri, endpointUrl, metricIri, escapeLiteral(metricName));
        } catch (Exception e) {
            throw new KnowledgeBaseException("registerMetricMetadata failed: " + e.getMessage(), e);
        }
    }

    private void validateIri(String iri, String field) throws KnowledgeBaseException {
        if (iri == null || (!iri.startsWith("http://") && !iri.startsWith("https://"))) {
            throw new KnowledgeBaseException(field + " is not an absolute IRI: " + iri);
        }
    }

    private String inverseLocalName(String predicate) {
        String localName = predicate.substring(cneeNamespace.length());
        return switch (localName) {
            case CneeOntology.PROP_CONTAINS -> CneeOntology.PROP_IS_PART_OF;
            case CneeOntology.PROP_IS_PART_OF -> CneeOntology.PROP_CONTAINS;
            case CneeOntology.PROP_HOSTS -> CneeOntology.PROP_IS_HOSTED_ON;
            case CneeOntology.PROP_IS_HOSTED_ON -> CneeOntology.PROP_HOSTS;
            case CneeOntology.PROP_COMMUNICATES_WITH -> CneeOntology.PROP_COMMUNICATES_WITH;
            default -> null;
        };
    }

    private boolean isValidCneeIri(String iri) {
        return iri == null || !iri.startsWith(cneeNamespace);
    }

    private String localNameOf(String iri) {
        if (iri == null || iri.isBlank()) return null;
        int hash = iri.lastIndexOf('#');
        int slash = iri.lastIndexOf('/');
        int idx = Math.max(hash, slash);
        if (idx < 0 || idx >= iri.length() - 1) return null;
        return iri.substring(idx + 1);
    }

    private String escapeLiteral(String raw) {
        if (raw == null) return "";
        return raw.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private String encodeFragment(String raw) {
        return URLEncoder.encode(raw, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
