package com.kubiki.palamedes.knowledge;

import com.kubiki.daedalus.annotation.*;

@DaedalusRepository
public interface SparqlRepository {
    @Template(resource = "sparql/find-anomalies.sparql")
    String findAnomalies();

    @Template(resource = "sparql/find-dependents.sparql")
    String findDependents(@Type(TemplateType.IRI) @Bind("IRI::actionId") String actionId);

    @Template(resource = "sparql/find-compensation.sparql")
    String findCompensation(@Type(TemplateType.IRI) @Bind("IRI::actionId") String actionId);

    @Template(resource = "sparql/find-active-actions.sparql")
    String findActiveActions();

    @Template(resource = "sparql/fetch-action-structure.sparql")
    String fetchActionStructure(@Type(TemplateType.IRI) @Bind("IRI::actionId") String actionId);

    @Template(resource = "sparql/check-idempotency.sparql")
    String checkIdempotency(@Type(TemplateType.IRI) @Bind("IRI::actionId") String actionId);

    @Template(resource = "sparql/atomic-transition.sparql")
    String atomicTransition(
            @Type(TemplateType.IRI) @Bind("IRI::actionId") String actionId,
            @Type(TemplateType.IRI) @Bind("IRI::fromState") String fromState,
            @Type(TemplateType.IRI) @Bind("IRI::toState") String toState
    );
}
