package com.kubiki.palamedes.saga;

import com.kubiki.common.model.ActionStatusUpdate;
import com.kubiki.common.model.ExecutionStatus;
import com.kubiki.palamedes.condition.ConditionFactory;
import com.kubiki.palamedes.knowledge.GraphDBGateway;
import com.kubiki.common.ontology.OntologyRegistry;
import com.kubiki.palamedes.knowledge.StateRepository;
import com.kubiki.palamedes.model.ActionData;
import com.kubiki.palamedes.model.WorkflowState;
import com.kubiki.palamedes.model.WorkflowStateMapper;
import com.kubiki.palamedes.utils.ActionUtils;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SagaManagerTest {
    private final IRI actionIri = SimpleValueFactory.getInstance().createIRI("http://test/action1");
    private final IRI dependentIri = SimpleValueFactory.getInstance().createIRI("http://test/dependent1");
    @Mock
    private GraphDBGateway gateway;
    @Mock
    private StateRepository stateRepository;
    @Mock
    private OntologyRegistry registry;
    @Mock
    private ConditionFactory conditionFactory;
    @Mock
    private ActionUtils actionUtils;
    @Mock
    private WorkflowStateMapper mapper;
    @Mock
    private ApplicationEventPublisher publisher;
    private SagaManager sagaManager;

    @BeforeEach
    void setUp() {
        SagaTransitionHandler targetHandler = new SagaTransitionHandler(actionUtils, gateway, stateRepository, mapper);
        
        AspectJProxyFactory factory = new AspectJProxyFactory(targetHandler);
        com.kubiki.palamedes.aspect.PalamedesAspects aspects = new com.kubiki.palamedes.aspect.PalamedesAspects(gateway, stateRepository);
        factory.addAspect(aspects);
        
        SagaTransitionHandler transitionHandlerProxy = factory.getProxy();
        
        sagaManager = new SagaManager(gateway, registry, conditionFactory, publisher, transitionHandlerProxy);

        when(registry.actionsOntology(anyString())).thenReturn(actionIri);
    }

    @Test
    void shouldUnlockNextStepsOnSuccess() {
        ActionStatusUpdate update = new ActionStatusUpdate("action1", ExecutionStatus.COMPLETED, null, 200);

        ActionData data = mock(ActionData.SimpleAction.class);
        when(data.postConditions()).thenReturn(List.of());
        when(gateway.fetchActionStructure(actionIri)).thenReturn(data);

        when(stateRepository.transition(eq(actionIri), eq(WorkflowState.IN_PROGRESS), eq(WorkflowState.SUCCEEDED))).thenReturn(true);
        when(gateway.findDependents(actionIri)).thenReturn(List.of(dependentIri));

        sagaManager.handleFeedback(update);

        verify(gateway).findDependents(actionIri);
        verify(gateway).transitionState(dependentIri, mapper.getFragment(WorkflowState.INITIAL));
    }

    @Test
    void shouldTriggerCompensationOnFailure() {
        ActionStatusUpdate update = new ActionStatusUpdate("action1", ExecutionStatus.FAILED_HTTP, "Error", 500);
        when(stateRepository.transition(eq(actionIri), eq(WorkflowState.IN_PROGRESS), eq(WorkflowState.FAILED))).thenReturn(true);

        IRI compensationIri = SimpleValueFactory.getInstance().createIRI("http://test/rollback");
        when(gateway.findCompensation(actionIri)).thenReturn(compensationIri);

        ActionData originalAction = mock(ActionData.SimpleAction.class);
        IRI targetIri = SimpleValueFactory.getInstance().createIRI("http://test/pod");
        when(originalAction.target()).thenReturn(targetIri);
        when(gateway.fetchActionStructure(actionIri)).thenReturn(originalAction);

        when(actionUtils.generateCompensationId()).thenReturn("mocked-compensation-id-123");

        sagaManager.handleFeedback(update);

        verify(gateway, atLeastOnce()).createActionWorkflow(eq(targetIri), eq(compensationIri), anyString());
    }
}
