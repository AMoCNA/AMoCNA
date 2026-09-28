package com.kubiki.metrics.graph;

import com.kubiki.daedalus.annotation.*;

@DaedalusRepository
public interface SparqlRepository {
    @Template(resource = "sparql/instantiate-anomaly.sparql")
    String instantiateAnomaly(
            @Type(TemplateType.IRI) @Bind("IRI::targetResource") String targetResource,
            @Type(TemplateType.IRI) @Bind("IRI::anomaly") String anomaly,
            @Type(TemplateType.IRI) @Bind("IRI::anomalyType") String anomalyType,
            @Bind("LITERAL::timestamp") String timestamp
    );

    @Template(resource = "sparql/clear-anomalies.sparql")
    String clearAnomalies(
            @Type(TemplateType.IRI) @Bind("IRI::targetResource") String targetResource,
            @Type(TemplateType.IRI) @Bind("IRI::anomalyType") String anomalyType
    );
}
