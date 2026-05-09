package com.kubiki.themis.saga;

import com.kubiki.themis.execution.ActionDispatcher;
import com.kubiki.themis.knowledge.GraphDBGateway;
import com.kubiki.themis.model.ActionData;
import com.kubiki.themis.model.ExecutionStatus;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpMethod;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.kubiki.themis.model.Protocol.REST;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("SagaEngine Tests")
class SagaEngineTest {

    private static final SimpleValueFactory VF = SimpleValueFactory.getInstance();

    private GraphDBGateway gateway;
    private ActionDispatcher dispatcher;
    private SagaEngine engine;

    @BeforeEach
    void setUp() {
        gateway = Mockito.mock(GraphDBGateway.class);
        dispatcher = Mockito.mock(ActionDispatcher.class);
        engine = new SagaEngine(gateway);
    }

    @Test
    @DisplayName("Should execute simple action successfully")
    void executesSimpleActionSuccessfully() {
        ActionData.SimpleAction action = ActionData.SimpleAction
                .builder()
                .id(VF.createIRI("http://moam#id"))
                .functionalIntent("intent")
                .protocol(REST)
                .instruction("url")
                .targetIri(VF.createIRI("http://cnee#target"))
                .data(Map.of())
                .method(HttpMethod.GET)
                .payload(null)
                .preConditions(List.of())
                .postConditions(List.of())
                .build();

        UUID executionId = UUID.randomUUID();

        when(dispatcher.dispatchSimple(action, executionId)).thenReturn(true);

        boolean result = engine.execute(action, executionId, dispatcher);

        assertTrue(result);
        verify(gateway).updateActionState(VF.createIRI("http://moam#id"), ExecutionStatus.IN_PROGRESS);
        verify(gateway).updateActionState(VF.createIRI("http://moam#id"), ExecutionStatus.SUCCESS);
    }

    @Test
    @DisplayName("Should compensate on workflow failure")
    void compensatesOnWorkflowFailure() {
        ActionData.SimpleAction step1 = ActionData.SimpleAction
                .builder()
                .id(VF.createIRI("http://moam#step1"))
                .functionalIntent("intent")
                .protocol(REST)
                .instruction("url")
                .targetIri(VF.createIRI("http://cnee#target"))
                .data(Map.of())
                .method(HttpMethod.GET)
                .payload(null)
                .preConditions(List.of())
                .postConditions(List.of())
                .build();

        ActionData.SimpleAction step2 = ActionData.SimpleAction
                .builder()
                .id(VF.createIRI("http://moam#step2"))
                .functionalIntent("intent")
                .protocol(REST)
                .instruction("url")
                .targetIri(VF.createIRI("http://cnee#target"))
                .data(Map.of())
                .method(HttpMethod.GET)
                .payload(null)
                .preConditions(List.of())
                .postConditions(List.of())
                .build();

        ActionData.SimpleAction comp1 = ActionData.SimpleAction
                .builder()
                .id(VF.createIRI("http://moam#comp1"))
                .functionalIntent("intent")
                .protocol(REST)
                .instruction("url")
                .targetIri(VF.createIRI("http://cnee#target"))
                .data(Map.of())
                .method(HttpMethod.GET)
                .payload(null)
                .preConditions(List.of())
                .postConditions(List.of())
                .build();

        ActionData.ComplexWorkflow workflow = new ActionData.ComplexWorkflow(
                VF.createIRI("http://moam#wf"),
                "intent",
                List.of(step1, step2),
                Map.of(step1.id(), comp1)
        );

        UUID executionId = UUID.randomUUID();

        when(dispatcher.dispatchSimple(step1, executionId)).thenReturn(true);
        when(dispatcher.dispatchSimple(step2, executionId)).thenReturn(false);
        when(dispatcher.dispatchSimple(comp1, executionId)).thenReturn(true);

        boolean result = engine.execute(workflow, executionId, dispatcher);

        assertFalse(result);
        verify(gateway).updateActionState(workflow.id(), ExecutionStatus.IN_PROGRESS);
        verify(gateway).updateActionState(step1.id(), ExecutionStatus.IN_PROGRESS);
        verify(gateway).updateActionState(step1.id(), ExecutionStatus.SUCCESS);
        verify(gateway).updateActionState(step2.id(), ExecutionStatus.IN_PROGRESS);
        verify(gateway).updateActionState(step2.id(), ExecutionStatus.FAILED);
        verify(gateway).updateActionState(workflow.id(), ExecutionStatus.FAILED);

        verify(gateway).updateActionState(comp1.id(), ExecutionStatus.IN_PROGRESS);
        verify(gateway).updateActionState(comp1.id(), ExecutionStatus.SUCCESS);
    }
}