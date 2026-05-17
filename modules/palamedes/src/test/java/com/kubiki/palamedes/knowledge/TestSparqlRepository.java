package com.kubiki.palamedes.knowledge;

import com.kubiki.daedalus.annotation.*;

@DaedalusRepository
public interface TestSparqlRepository {
    @Template(resource = "sparql/query-to-template.sparql")
    String hydrateTestQuery(@Type(TemplateType.IRI) @Bind("IRI::actionId") String actionId);
}
