package com.kubiki.themis.execution;

import com.kubiki.themis.condition.ConditionEvaluator;
import com.kubiki.themis.model.ActionData;
import com.kubiki.themis.model.Protocol;
import com.kubiki.themis.saga.SagaEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("ActionDispatcher Unit Tests")
class ActionDispatcherTest {

    @Test
    @DisplayName("Should evaluate pre and post conditions successfully")
    void shouldEvaluatePreAndPostConditions() {
        ProtocolExecutor executor = mock(ProtocolExecutor.class);
        when(executor.supports(Protocol.REST)).thenReturn(true);
        when(executor.execute(any(), any())).thenReturn(true);

        ConditionEvaluator evaluator = mock(ConditionEvaluator.class);
        when(evaluator.supports("TypeA")).thenReturn(true);
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

        ActionData.ConditionData pre = new ActionData.ConditionData("pre1", "TypeA", "ASK");
        ActionData.ConditionData post = new ActionData.ConditionData("post1", "TypeA", "ASK");

        ActionData.SimpleAction action = new ActionData.SimpleAction(
                "action1", "Intent", Protocol.REST, "url", "target", Map.of(), HttpMethod.GET, null, List.of(pre), List.of(post)
        );

        boolean result = dispatcher.dispatch(action, UUID.randomUUID());

        assertAll("Pre/Post Condition Success Validation",
                () -> assertTrue(result, "Action should succeed"),
                () -> verify(evaluator, times(2)).evaluate(any()),
                () -> verify(executor).execute(any(), any())
        );
    }

    @Test
    @DisplayName("Should fail execution if pre-condition fails")
    void shouldFailIfPreConditionFails() {
        ProtocolExecutor executor = mock(ProtocolExecutor.class);
        ConditionEvaluator evaluator = mock(ConditionEvaluator.class);
        when(evaluator.supports("TypeA")).thenReturn(true);
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

        ActionData.ConditionData pre = new ActionData.ConditionData("pre1", "TypeA", "ASK");

        ActionData.SimpleAction action = new ActionData.SimpleAction(
                "action1", "Intent", Protocol.REST, "url", "target", Map.of(), HttpMethod.GET, null, List.of(pre), List.of()
        );

        boolean result = dispatcher.dispatch(action, UUID.randomUUID());

        assertAll("Pre-condition Failure Validation",
                () -> assertFalse(result, "Action should fail"),
                () -> verify(executor, never()).execute(any(), any())
        );
    }

    @Test
    @DisplayName("Should succeed if conditions are null or empty")
    void shouldSucceedIfConditionsAreNullOrEmpty() {
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
        ActionData.SimpleAction actionNull = new ActionData.SimpleAction(
                "a1", "I", Protocol.REST, "u", "t", Map.of(), HttpMethod.GET, null, null, null
        );

        // Test empty
        ActionData.SimpleAction actionEmpty = new ActionData.SimpleAction(
                "a2", "I", Protocol.REST, "u", "t", Map.of(), HttpMethod.GET, null, List.of(), List.of()
        );

        assertAll("Empty/Null Conditions Validation",
                () -> assertTrue(dispatcher.dispatch(actionNull, UUID.randomUUID()), "Should succeed with null conditions"),
                () -> assertTrue(dispatcher.dispatch(actionEmpty, UUID.randomUUID()), "Should succeed with empty conditions")
        );
    }

    @Test
    @DisplayName("Should fail if no evaluator is found for a condition")
    void shouldFailIfNoEvaluatorFound() {
        ProtocolExecutor executor = mock(ProtocolExecutor.class);
        SagaEngine sagaEngine = mock(SagaEngine.class);
        when(sagaEngine.execute(any(), any(), any())).thenAnswer(invocation -> {
            ActionData.SimpleAction simple = invocation.getArgument(0);
            return ((ActionDispatcher) invocation.getArgument(2)).dispatchSimple(simple, invocation.getArgument(1));
        });

        ActionDispatcher dispatcher = new ActionDispatcher(List.of(executor), List.of(), sagaEngine);

        ActionData.ConditionData pre = new ActionData.ConditionData("pre1", "UnknownType", "ASK");
        ActionData.SimpleAction action = new ActionData.SimpleAction(
                "a1", "I", Protocol.REST, "u", "t", Map.of(), HttpMethod.GET, null, List.of(pre), List.of()
        );

        assertFalse(dispatcher.dispatch(action, UUID.randomUUID()), "Should fail when evaluator is missing");
    }

    @Test
    @DisplayName("Should fail if no executor is found for a protocol")
    void shouldFailIfNoExecutorFound() {
        SagaEngine sagaEngine = mock(SagaEngine.class);
        when(sagaEngine.execute(any(), any(), any())).thenAnswer(invocation -> {
            ActionData.SimpleAction simple = invocation.getArgument(0);
            return ((ActionDispatcher) invocation.getArgument(2)).dispatchSimple(simple, invocation.getArgument(1));
        });

        ActionDispatcher dispatcher = new ActionDispatcher(List.of(), List.of(), sagaEngine);

        ActionData.SimpleAction action = new ActionData.SimpleAction(
                "a1", "I", Protocol.REST, "u", "t", Map.of(), HttpMethod.GET, null, List.of(), List.of()
        );

        assertFalse(dispatcher.dispatch(action, UUID.randomUUID()), "Should fail when executor is missing");
    }
}
