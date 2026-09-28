package com.kubiki.palamedes.knowledge;

import com.kubiki.daedalus.annotation.*;
import org.eclipse.rdf4j.query.BindingSet;

import java.util.List;

@DaedalusRepository
public interface SparqlRepository {
    @SparqlQuery(resource = "sparql/find-anomalies.sparql")
    List<BindingSet> findAnomalies();

    @SparqlQuery(resource = "sparql/find-dependents.sparql")
    List<BindingSet> findDependents(@Type(TemplateType.IRI) @Bind("IRI::actionId") String actionId);

    @SparqlQuery(resource = "sparql/find-compensation.sparql")
    List<BindingSet> findCompensation(@Type(TemplateType.IRI) @Bind("IRI::actionId") String actionId);

    @SparqlQuery(resource = "sparql/find-active-actions.sparql")
    List<BindingSet> findActiveActions();

    @SparqlQuery(resource = "sparql/fetch-action-structure.sparql")
    List<BindingSet> fetchActionStructure(@Type(TemplateType.IRI) @Bind("IRI::actionId") String actionId);

    @SparqlQuery(resource = "sparql/fetch-action-structures.sparql")
    List<BindingSet> fetchActionStructures(@Type(TemplateType.PLAIN) @Bind("IRI::actionIds") String actionIds);

    @SparqlQuery(resource = "sparql/fetch-action-hydrations.sparql")
    List<BindingSet> fetchActionHydrations(@Type(TemplateType.PLAIN) @Bind("IRI::actionIds") String actionIds);

    @SparqlQuery(resource = "sparql/check-idempotency.sparql")
    List<BindingSet> checkIdempotency(@Type(TemplateType.IRI) @Bind("IRI::actionId") String actionId);

    @SparqlQuery(resource = "sparql/find-recent-action.sparql")
    List<BindingSet> findRecentAction(
            @Type(TemplateType.IRI) @Bind("IRI::target") String target,
            @Type(TemplateType.IRI) @Bind("IRI::intent") String intent
    );

    @SparqlQuery(resource = "sparql/check-idempotency-batch.sparql")
    List<BindingSet> checkIdempotencyBatch(@Type(TemplateType.PLAIN) @Bind("IRI::actionIds") String actionIds);

    @SparqlUpdate(resource = "sparql/atomic-transition.sparql")
    void atomicTransition(
            @Type(TemplateType.IRI) @Bind("IRI::actionId") String actionId,
            @Type(TemplateType.IRI) @Bind("IRI::fromState") String fromState,
            @Type(TemplateType.IRI) @Bind("IRI::toState") String toState
    );

    @SparqlUpdate(resource = "sparql/update-resource-state.sparql")
    void updateResourceState(
            @Type(TemplateType.IRI) @Bind("IRI::resourceId") String resourceId,
            @Type(TemplateType.IRI) @Bind("IRI::toState") String toState
    );

    @SparqlUpdate(resource = "sparql/clear-resource-state.sparql")
    void clearResourceState(
            @Type(TemplateType.IRI) @Bind("IRI::resourceId") String resourceId
    );

    @SparqlQuery(resource = "sparql/find-root-cause.sparql")
    List<BindingSet> findRootCause(@Type(TemplateType.IRI) @Bind("IRI::startResource") String startResource);

    @SparqlQuery(resource = "sparql/find-vulnerable-workloads.sparql")
    List<BindingSet> findVulnerableWorkloads(@Type(TemplateType.PLAIN) @Bind("PLAIN::vulnerablePairs") String vulnerablePairs);

    @SparqlQuery(resource = "sparql/fetch-workload-details.sparql")
    List<BindingSet> fetchWorkloadDetails(@Type(TemplateType.IRI) @Bind("IRI::workloadIri") String workloadIri);

    @SparqlQuery(resource = "sparql/find-registry-auth-failures.sparql")
    List<BindingSet> findRegistryAuthFailures();

    @SparqlQuery(resource = "sparql/fetch-intent-metadata.sparql")
    List<BindingSet> fetchIntentMetadata(@Type(TemplateType.PLAIN) @Bind("IRI::intentIds") String intentIds);

    @SparqlQuery(resource = "sparql/is-dependent-resource.sparql")
    boolean isDependentResource(
            @Type(TemplateType.IRI) @Bind("IRI::source") String source,
            @Type(TemplateType.IRI) @Bind("IRI::target") String target
    );

    @SparqlQuery(resource = "sparql/fetch-action-states.sparql")
    List<BindingSet> fetchActionStates(@Type(TemplateType.PLAIN) @Bind("IRI::actionIds") String actionIds);

    @SparqlUpdate(resource = "sparql/save-outbox-event.sparql")
    void saveOutboxEvent(
            @Type(TemplateType.IRI) @Bind("IRI::eventIri") String eventIri,
            @Type(TemplateType.LITERAL) @Bind("LITERAL::payload") String payload,
            @Type(TemplateType.LITERAL) @Bind("LITERAL::timestamp") String timestamp
    );

    @SparqlQuery(resource = "sparql/find-pending-outbox-events.sparql")
    List<BindingSet> findPendingEvents();

    @SparqlUpdate(resource = "sparql/delete-outbox-event.sparql")
    void deleteOutboxEvent(@Type(TemplateType.IRI) @Bind("IRI::eventIri") String eventIri);
}

