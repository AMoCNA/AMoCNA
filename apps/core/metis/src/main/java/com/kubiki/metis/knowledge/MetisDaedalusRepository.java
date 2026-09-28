package com.kubiki.metis.knowledge;

import com.kubiki.daedalus.annotation.*;

@DaedalusRepository
public interface MetisDaedalusRepository {

    @Template(resource = "sparql/insert-entity.sparql")
    String insertEntity(
            @Type(TemplateType.IRI) @Bind("IRI::resourceIri") String resourceIri,
            @Type(TemplateType.IRI) @Bind("IRI::ontologyType") String ontologyType,
            @Bind("resourceId") String resourceId,
            @Bind("resourceName") String resourceName,
            @Type(TemplateType.PLAIN) @Bind("triples") String triples
    );

    @Template(resource = "sparql/assert-relationship.sparql")
    String assertRelationship(
            @Type(TemplateType.IRI) @Bind("IRI::subjectIri") String subjectIri,
            @Type(TemplateType.PLAIN) @Bind("predicate") String predicate,
            @Type(TemplateType.IRI) @Bind("IRI::objectIri") String objectIri
    );

    @Template(resource = "sparql/assert-relationship-pair.sparql")
    String assertRelationshipPair(
            @Type(TemplateType.IRI) @Bind("IRI::subjectIri") String subjectIri,
            @Type(TemplateType.PLAIN) @Bind("predicate") String predicate,
            @Type(TemplateType.IRI) @Bind("IRI::objectIri") String objectIri,
            @Type(TemplateType.PLAIN) @Bind("inversePredicate") String inversePredicate
    );

    @Template(resource = "sparql/change-state.sparql")
    String changeState(
            @Type(TemplateType.IRI) @Bind("IRI::resourceIri") String resourceIri,
            @Type(TemplateType.IRI) @Bind("IRI::newStateIri") String newStateIri
    );

    @Template(resource = "sparql/delete-entity.sparql")
    String deleteEntity(
            @Type(TemplateType.IRI) @Bind("IRI::resourceIri") String resourceIri
    );

    @Template(resource = "sparql/register-metric.sparql")
    String registerMetricMetadata(
            @Type(TemplateType.IRI) @Bind("IRI::resourceIri") String resourceIri,
            @Bind("endpointUrl") String endpointUrl,
            @Type(TemplateType.IRI) @Bind("IRI::metricIri") String metricIri,
            @Bind("metricName") String metricName
    );
}
