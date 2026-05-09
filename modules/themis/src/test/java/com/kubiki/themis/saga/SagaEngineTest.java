package com.kubiki.themis.saga;

import com.kubiki.themis.execution.ActionDispatcher;
import com.kubiki.themis.knowledge.GraphDBGateway;
import com.kubiki.themis.model.ActionData;
import com.kubiki.themis.model.ExecutionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.http.HttpMethod;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.kubiki.themis.model.Protocol.REST;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SagaEngineTest {

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
    void executesSimpleActionSuccessfully() {
        ActionData.SimpleAction action = new ActionData.SimpleAction("id", "intent", REST, "url", "target", Map.of(), HttpMethod.GET, null, List.of(), List.of());
        UUID executionId = UUID.randomUUID();

        when(dispatcher.dispatchSimple(action, executionId)).thenReturn(true);

        boolean result = engine.execute(action, executionId, dispatcher);

        assertTrue(result);
        verify(gateway).updateActionState("id", ExecutionStatus.IN_PROGRESS);
        verify(gateway).updateActionState("id", ExecutionStatus.SUCCESS);
    }

    @Test
    void compensatesOnWorkflowFailure() {
        ActionData.SimpleAction step1 = new ActionData.SimpleAction("step1", "intent", REST, "url", "target", Map.of(), HttpMethod.GET, null, List.of(), List.of());
        ActionData.SimpleAction step2 = new ActionData.SimpleAction("step2", "intent", REST, "url", "target", Map.of(), HttpMethod.GET, null, List.of(), List.of());
        ActionData.SimpleAction comp1 = new ActionData.SimpleAction("comp1", "intent", REST, "url", "target", Map.of(), HttpMethod.GET, null, List.of(), List.of());

        ActionData.ComplexWorkflow workflow = new ActionData.ComplexWorkflow(
            "wf", "intent", List.of(step1, step2), Map.of("step1", comp1)
        );
        UUID executionId = UUID.randomUUID();

        // Step 1 succeeds, Step 2 fails, comp1 succeeds
        when(dispatcher.dispatchSimple(step1, executionId)).thenReturn(true);
        when(dispatcher.dispatchSimple(step2, executionId)).thenReturn(false);
        when(dispatcher.dispatchSimple(comp1, executionId)).thenReturn(true);

        boolean result = engine.execute(workflow, executionId, dispatcher);

        assertFalse(result); // workflow failed
        verify(gateway).updateActionState("wf", ExecutionStatus.IN_PROGRESS);
        verify(gateway).updateActionState("step1", ExecutionStatus.IN_PROGRESS);
        verify(gateway).updateActionState("step1", ExecutionStatus.SUCCESS);
        verify(gateway).updateActionState("step2", ExecutionStatus.IN_PROGRESS);
        verify(gateway).updateActionState("step2", ExecutionStatus.FAILED);
        verify(gateway).updateActionState("wf", ExecutionStatus.FAILED);

        // Check compensation
        verify(gateway).updateActionState("comp1", ExecutionStatus.IN_PROGRESS);
        verify(gateway).updateActionState("comp1", ExecutionStatus.SUCCESS);
    }
}
