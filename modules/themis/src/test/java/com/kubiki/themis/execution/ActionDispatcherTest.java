package com.kubiki.themis.execution;

import com.kubiki.themis.condition.ConditionEvaluator;
import com.kubiki.themis.model.ActionData;
import com.kubiki.themis.saga.SagaEngine;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ActionDispatcherTest {

    @Test
    void shouldEvaluatePreAndPostConditions() {
        ProtocolExecutor executor = mock(ProtocolExecutor.class);
        when(executor.supports("REST")).thenReturn(true);
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
                "action1", "Intent", "REST", "url", "target", Map.of(), "GET", null, List.of(pre), List.of(post)
        );

        boolean result = dispatcher.dispatch(action, UUID.randomUUID());

        assertTrue(result);
        verify(evaluator, times(2)).evaluate(any());
        verify(executor).execute(any(), any());
    }

    @Test
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
                "action1", "Intent", "REST", "url", "target", Map.of(), "GET", null, List.of(pre), List.of()
        );

        boolean result = dispatcher.dispatch(action, UUID.randomUUID());

        assertFalse(result);
        verify(executor, never()).execute(any(), any());
    }
}
