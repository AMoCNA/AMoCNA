package com.kubiki.palamedes.knowledge;

import com.kubiki.palamedes.model.WorkflowState;
import com.kubiki.palamedes.model.WorkflowStateMapper;
import com.kubiki.palamedes.saga.SagaStateMachine;
import lombok.RequiredArgsConstructor;
import org.eclipse.rdf4j.model.IRI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class StateRepository {
    private static final Logger log = LoggerFactory.getLogger(StateRepository.class);

    private final SparqlRepository sparqlRepository;
    private final com.kubiki.common.ontology.OntologyRegistry ontologyRegistry;
    private final WorkflowStateMapper mapper;

    public boolean transition(IRI actionId, WorkflowState from, WorkflowState to) {
        SagaStateMachine.Event event = null;
        if (from == WorkflowState.VALIDATED && to == WorkflowState.IN_PROGRESS) {
            event = SagaStateMachine.Event.DISPATCH;
        } else if (from == WorkflowState.IN_PROGRESS && to == WorkflowState.SUCCEEDED) {
            event = SagaStateMachine.Event.EXECUTE_SUCCESS;
        } else if (from == WorkflowState.IN_PROGRESS && to == WorkflowState.FAILED) {
            event = SagaStateMachine.Event.EXECUTE_FAILURE;
        } else if (to == WorkflowState.COMPENSATING) {
            event = SagaStateMachine.Event.COMPENSATE;
        } else if (from == WorkflowState.PLANNED && to == WorkflowState.SUCCEEDED) {
            event = SagaStateMachine.Event.EXECUTE_SUCCESS;
        } else if (from == WorkflowState.INITIAL && to == WorkflowState.PLANNED) {
            event = SagaStateMachine.Event.PLAN;
        } else if (from == WorkflowState.PLANNED && to == WorkflowState.VALIDATED) {
            event = SagaStateMachine.Event.VALIDATE;
        }

        if (event != null) {
            WorkflowState expectedNext = SagaStateMachine.getNextState(from, event).orElse(null);
            if (expectedNext != null && expectedNext != to) {
                log.warn("SagaStateMachine: Overriding invalid transition from {} to {} on event {} to expected next: {}",
                        from, to, event, expectedNext);
                to = expectedNext;
            }
        }

        log.debug("StateRepository: Performing transition for action {} from {} to {}", actionId, from, to);
        sparqlRepository.atomicTransition(
                actionId.stringValue(),
                ontologyRegistry.actionsOntology(mapper.getFragment(from)).stringValue(),
                ontologyRegistry.actionsOntology(mapper.getFragment(to)).stringValue()
        );

        return true;
    }
}
