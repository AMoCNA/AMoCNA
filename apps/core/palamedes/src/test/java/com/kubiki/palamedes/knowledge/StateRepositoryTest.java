package com.kubiki.palamedes.knowledge;

import com.kubiki.palamedes.model.WorkflowState;
import com.kubiki.palamedes.model.WorkflowStateMapper;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StateRepositoryTest {
    private final IRI actionId = SimpleValueFactory.getInstance().createIRI("http://test/action1");
    @Mock
    private SparqlRepository sparqlRepository;
    @Mock
    private com.kubiki.common.ontology.OntologyRegistry registry;
    @Mock
    private WorkflowStateMapper mapper;
    private StateRepository repository;

    @BeforeEach
    void setUp() {
        repository = new StateRepository(sparqlRepository, registry, mapper);
    }

    @Test
    void shouldTransitionState() {
        when(registry.actionsOntology(anyString())).thenReturn(SimpleValueFactory.getInstance().createIRI("http://test/State"));

        when(mapper.getFragment(WorkflowState.INITIAL)).thenReturn("State_Initial");
        when(mapper.getFragment(WorkflowState.PLANNED)).thenReturn("State_Planned");

        boolean result = repository.transition(actionId, WorkflowState.INITIAL, WorkflowState.PLANNED);

        assertTrue(result);
        verify(sparqlRepository).atomicTransition(anyString(), anyString(), anyString());
    }
}
