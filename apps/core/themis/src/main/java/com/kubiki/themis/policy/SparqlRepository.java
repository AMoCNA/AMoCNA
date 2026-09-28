package com.kubiki.themis.policy;

import com.kubiki.daedalus.annotation.*;
import org.eclipse.rdf4j.query.BindingSet;

import java.util.List;

@DaedalusRepository
public interface SparqlRepository {
    @SparqlQuery(resource = "sparql/fetch-conditions.sparql")
    List<BindingSet> fetchConditions(
            @Type(TemplateType.IRI) @Bind("IRI::action") String actionIri,
            @Type(TemplateType.IRI) @Bind("IRI::property") String propertyIri
    );
}
