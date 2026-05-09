package com.kubiki.themis.execution;

import com.kubiki.themis.condition.ConditionEvaluator;
import com.kubiki.themis.model.ActionData;
import com.kubiki.themis.model.Protocol;
import com.kubiki.themis.saga.SagaEngine;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.kubiki.themis.testutil.TestActionBuilder.simpleAction;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("ActionDispatcher Tests")
class ActionDispatcherTest {
    private static final SimpleValueFactory VF = SimpleValueFactory.getInstance();

    @Test
    @DisplayName("Should succeed when pre and post conditions are met")
    void shouldSucceedWhenPreAndPostConditionsAreMet() {
        ProtocolExecutor executor = mock(ProtocolExecutor.class);
        when(executor.supports(Protocol.REST)).thenReturn(true);
        when(executor.execute(any(), any())).thenReturn(true);

        IRI typeA = VF.createIRI("http://moa#TypeA");
        ConditionEvaluator evaluator = mock(ConditionEvaluator.class);
        when(evaluator.supports(typeA)).thenReturn(true);
        when(evaluator.evaluate(any())).thenReturn(true);

        SagaEngine sagaEngine = mock(SagaEngine.class);
        when(sagaEngine.execute(any(), any(), any())).thenAnswer(invocation -> {
            ActionData action = invocation.getArgument(0);
            UUID executionId = invocation.getArgument(1);
            ActionDispatcher dispatcherArg = invocation.getArgument(2);
            if (action instanceof ActionData.SimpleAction simple) {
                return dispatcherArg.dispatchSimple(simple, executionId);
            }
            return false;
        });

        ActionDispatcher dispatcher = new ActionDispatcher(List.of(executor), List.of(evaluator), sagaEngine);

        ActionData.ConditionData pre = new ActionData.ConditionData(VF.createIRI("http://moa#pre1"), typeA, "ASK");
        ActionData.ConditionData post = new ActionData.ConditionData(VF.createIRI("http://moa#post1"), typeA, "ASK");

        ActionData.SimpleAction action = simpleAction()
                .id(VF.createIRI("http://moa#action1"))
                .functionalIntent("Intent")
                .protocol(Protocol.REST)
                .instruction("url")
                .targetIri(VF.createIRI("http://target"))
                .method(HttpMethod.GET)
                .preConditions(List.of(pre))
                .postConditions(List.of(post))
                .build();

        boolean result = dispatcher.dispatch(action, UUID.randomUUID());

        assertAll("Pre/Post Condition Success Validation",
                () -> assertTrue(result, "Action should succeed"),
                () -> verify(evaluator, times(2)).evaluate(any()),
                () -> verify(executor).execute(any(), any())
        );
    }

    @Test
    @DisplayName("Should fail when pre-condition fails")
    void shouldFailWhenPreConditionFails() {
        ProtocolExecutor executor = mock(ProtocolExecutor.class);
        IRI typeA = VF.createIRI("http://moa#TypeA");
        ConditionEvaluator evaluator = mock(ConditionEvaluator.class);
        when(evaluator.supports(typeA)).thenReturn(true);
        when(evaluator.evaluate(any())).thenReturn(false);

        SagaEngine sagaEngine = mock(SagaEngine.class);
        when(sagaEngine.execute(any(), any(), any())).thenAnswer(invocation -> {
            ActionData action = invocation.getArgument(0);
            UUID executionId = invocation.getArgument(1);
            ActionDispatcher dispatcherArg = invocation.getArgument(2);
            if (action instanceof ActionData.SimpleAction simple) {
                return dispatcherArg.dispatchSimple(simple, executionId);
            }
            return false;
        });

        ActionDispatcher dispatcher = new ActionDispatcher(List.of(executor), List.of(evaluator), sagaEngine);

        ActionData.ConditionData pre = new ActionData.ConditionData(VF.createIRI("http://moa#pre1"), typeA, "ASK");

        ActionData.SimpleAction action = simpleAction()
                .id(VF.createIRI("http://moa#action1"))
                .functionalIntent("Intent")
                .protocol(Protocol.REST)
                .instruction("url")
                .targetIri(VF.createIRI("http://target"))
                .method(HttpMethod.GET)
                .preConditions(List.of(pre))
                .build();

        boolean result = dispatcher.dispatch(action, UUID.randomUUID());

        assertAll("Pre-condition Failure Validation",
                () -> assertFalse(result, "Action should fail"),
                () -> verify(executor, never()).execute(any(), any())
        );
    }

    @Test
    @DisplayName("Should succeed when conditions are null or empty")
    void shouldSucceedWhenConditionsAreNullOrEmpty() {
        ProtocolExecutor executor = mock(ProtocolExecutor.class);
        when(executor.supports(Protocol.REST)).thenReturn(true);
        when(executor.execute(any(), any())).thenReturn(true);

        SagaEngine sagaEngine = mock(SagaEngine.class);
        when(sagaEngine.execute(any(), any(), any())).thenAnswer(invocation -> {
            ActionData.SimpleAction simple = invocation.getArgument(0);
            return ((ActionDispatcher) invocation.getArgument(2)).dispatchSimple(simple, invocation.getArgument(1));
        });

        ActionDispatcher dispatcher = new ActionDispatcher(List.of(executor), List.of(), sagaEngine);

        // Test null
        ActionData.SimpleAction actionNull = simpleAction()
                .id(VF.createIRI("http://moa#a1"))
                .functionalIntent("I")
                .protocol(Protocol.REST)
                .instruction("u")
                .targetIri(VF.createIRI("http://target"))
                .method(HttpMethod.GET)
                .preConditions(null)
                .postConditions(null)
                .build();

        // Test empty
        ActionData.SimpleAction actionEmpty = simpleAction()
                .id(VF.createIRI("http://moa#a2"))
                .functionalIntent("I")
                .protocol(Protocol.REST)
                .instruction("u")
                .targetIri(VF.createIRI("http://target"))
                .method(HttpMethod.GET)
                .build();

        assertAll("Empty/Null Conditions Validation",
                () -> assertTrue(dispatcher.dispatch(actionNull, UUID.randomUUID()), "Should succeed with null conditions"),
                () -> assertTrue(dispatcher.dispatch(actionEmpty, UUID.randomUUID()), "Should succeed with empty conditions")
        );
    }

    @Test
    @DisplayName("Should fail when no evaluator is found")
    void shouldFailWhenNoEvaluatorIsFound() {
        ProtocolExecutor executor = mock(ProtocolExecutor.class);
        SagaEngine sagaEngine = mock(SagaEngine.class);
        when(sagaEngine.execute(any(), any(), any())).thenAnswer(invocation -> {
            ActionData.SimpleAction simple = invocation.getArgument(0);
            return ((ActionDispatcher) invocation.getArgument(2)).dispatchSimple(simple, invocation.getArgument(1));
        });

        ActionDispatcher dispatcher = new ActionDispatcher(List.of(executor), List.of(), sagaEngine);

        ActionData.ConditionData pre = new ActionData.ConditionData(VF.createIRI("http://moa#pre1"), VF.createIRI("http://moa#UnknownType"), "ASK");
        ActionData.SimpleAction action = simpleAction()
                .id(VF.createIRI("http://moa#a1"))
                .functionalIntent("I")
                .protocol(Protocol.REST)
                .instruction("u")
                .targetIri(VF.createIRI("http://target"))
                .method(HttpMethod.GET)
                .preConditions(List.of(pre))
                .build();

        assertFalse(dispatcher.dispatch(action, UUID.randomUUID()), "Should fail when evaluator is missing");
    }

    @Test
    @DisplayName("Should fail when no executor is found")
    void shouldFailWhenNoExecutorIsFound() {
        SagaEngine sagaEngine = mock(SagaEngine.class);
        when(sagaEngine.execute(any(), any(), any())).thenAnswer(invocation -> {
            ActionData.SimpleAction simple = invocation.getArgument(0);
            return ((ActionDispatcher) invocation.getArgument(2)).dispatchSimple(simple, invocation.getArgument(1));
        });

        ActionDispatcher dispatcher = new ActionDispatcher(List.of(), List.of(), sagaEngine);

        ActionData.SimpleAction action = simpleAction()
                .id(VF.createIRI("http://moa#a1"))
                .functionalIntent("I")
                .protocol(Protocol.REST)
                .instruction("u")
                .targetIri(VF.createIRI("http://target"))
                .method(HttpMethod.GET)
                .build();

        assertFalse(dispatcher.dispatch(action, UUID.randomUUID()), "Should fail when executor is missing");
    }
}
