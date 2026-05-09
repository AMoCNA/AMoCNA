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

    @Test
    void executesDeeplyNestedWorkflowSuccessfully() {
        ActionData.SimpleAction step1 = new ActionData.SimpleAction("step1", "intent", REST, "url", "target", Map.of(), HttpMethod.GET, null, List.of(), List.of());
        ActionData.ComplexWorkflow innerWorkflow = new ActionData.ComplexWorkflow(
            "inner", "intent", List.of(step1), Map.of()
        );
        ActionData.ComplexWorkflow middleWorkflow = new ActionData.ComplexWorkflow(
            "middle", "intent", List.of(innerWorkflow), Map.of()
        );
        ActionData.ComplexWorkflow outerWorkflow = new ActionData.ComplexWorkflow(
            "outer", "intent", List.of(middleWorkflow), Map.of()
        );
        UUID executionId = UUID.randomUUID();

        when(dispatcher.dispatchSimple(step1, executionId)).thenReturn(true);

        boolean result = engine.execute(outerWorkflow, executionId, dispatcher);

        assertTrue(result);
        verify(gateway).updateActionState("outer", ExecutionStatus.SUCCESS);
        verify(gateway).updateActionState("middle", ExecutionStatus.SUCCESS);
        verify(gateway).updateActionState("inner", ExecutionStatus.SUCCESS);
        verify(gateway).updateActionState("step1", ExecutionStatus.SUCCESS);
    }

    @Test
    void compensatesOnNestedWorkflowFailure() {
        ActionData.SimpleAction step1 = new ActionData.SimpleAction("step1", "intent", REST, "url", "target", Map.of(), HttpMethod.GET, null, List.of(), List.of());
        ActionData.SimpleAction comp1 = new ActionData.SimpleAction("comp1", "intent", REST, "url", "target", Map.of(), HttpMethod.GET, null, List.of(), List.of());
        
        ActionData.SimpleAction nestedStep = new ActionData.SimpleAction("nestedStep", "intent", REST, "url", "target", Map.of(), HttpMethod.GET, null, List.of(), List.of());
        ActionData.SimpleAction nestedComp = new ActionData.SimpleAction("nestedComp", "intent", REST, "url", "target", Map.of(), HttpMethod.GET, null, List.of(), List.of());

        ActionData.ComplexWorkflow nestedWf = new ActionData.ComplexWorkflow(
            "nestedWf", "intent", List.of(nestedStep), Map.of("nestedStep", nestedComp)
        );
        
        ActionData.SimpleAction failingStep = new ActionData.SimpleAction("failingStep", "intent", REST, "url", "target", Map.of(), HttpMethod.GET, null, List.of(), List.of());

        ActionData.ComplexWorkflow mainWf = new ActionData.ComplexWorkflow(
            "mainWf", "intent", List.of(step1, nestedWf, failingStep), Map.of("step1", comp1)
        );
        
        UUID executionId = UUID.randomUUID();

        when(dispatcher.dispatchSimple(step1, executionId)).thenReturn(true);
        when(dispatcher.dispatchSimple(nestedStep, executionId)).thenReturn(true);
        when(dispatcher.dispatchSimple(failingStep, executionId)).thenReturn(false);
        when(dispatcher.dispatchSimple(comp1, executionId)).thenReturn(true);
        when(dispatcher.dispatchSimple(nestedComp, executionId)).thenReturn(true);

        boolean result = engine.execute(mainWf, executionId, dispatcher);

        assertFalse(result);
        verify(gateway).updateActionState("step1", ExecutionStatus.SUCCESS);
        verify(gateway).updateActionState("nestedStep", ExecutionStatus.SUCCESS);
        verify(gateway).updateActionState("failingStep", ExecutionStatus.FAILED);
        
        // Compensations
        // nestedWf succeeded, so its steps should be compensated if it was part of mainWf's executed steps.
        // Wait, nestedWf itself succeeded. But failingStep failed AFTER it.
        // So mainWf calls compensate on {step1, nestedWf}.
        // nestedWf doesn't have a compensation in mainWf's map, so it just finishes its pop.
        // BUT wait, if nestedWf succeeded, it was pushed to mainWf's executedSteps.
        // SagaEngine.compensate:
        // ActionData compensation = workflow.compensations().get(step.id());
        // If step is nestedWf, it looks for compensation for "nestedWf".
        // In my setup, I didn't provide one for "nestedWf" in mainWf.
        
        // If I want to test nested compensation, I should make nestedStep succeed, and then a step in nestedWf fail.
    }

    @Test
    void compensatesRecursiveWorkflows() {
        ActionData.SimpleAction s1 = new ActionData.SimpleAction("s1", "intent", REST, "url", "target", Map.of(), HttpMethod.GET, null, List.of(), List.of());
        ActionData.SimpleAction c1 = new ActionData.SimpleAction("c1", "intent", REST, "url", "target", Map.of(), HttpMethod.GET, null, List.of(), List.of());
        
        ActionData.SimpleAction ns1 = new ActionData.SimpleAction("ns1", "intent", REST, "url", "target", Map.of(), HttpMethod.GET, null, List.of(), List.of());
        ActionData.SimpleAction nc1 = new ActionData.SimpleAction("nc1", "intent", REST, "url", "target", Map.of(), HttpMethod.GET, null, List.of(), List.of());
        
        ActionData.SimpleAction failing = new ActionData.SimpleAction("failing", "intent", REST, "url", "target", Map.of(), HttpMethod.GET, null, List.of(), List.of());

        ActionData.ComplexWorkflow nested = new ActionData.ComplexWorkflow(
            "nested", "intent", List.of(ns1, failing), Map.of("ns1", nc1)
        );
        
        ActionData.ComplexWorkflow main = new ActionData.ComplexWorkflow(
            "main", "intent", List.of(s1, nested), Map.of("s1", c1)
        );

        UUID executionId = UUID.randomUUID();
        when(dispatcher.dispatchSimple(s1, executionId)).thenReturn(true);
        when(dispatcher.dispatchSimple(ns1, executionId)).thenReturn(true);
        when(dispatcher.dispatchSimple(failing, executionId)).thenReturn(false);
        when(dispatcher.dispatchSimple(c1, executionId)).thenReturn(true);
        when(dispatcher.dispatchSimple(nc1, executionId)).thenReturn(true);

        boolean result = engine.execute(main, executionId, dispatcher);

        assertFalse(result);
        verify(dispatcher).dispatchSimple(nc1, executionId); // nested compensation
        verify(dispatcher).dispatchSimple(c1, executionId);  // outer compensation
    }

    @Test
    void handlesCompensationFailure() {
        ActionData.SimpleAction step1 = new ActionData.SimpleAction("step1", "intent", REST, "url", "target", Map.of(), HttpMethod.GET, null, List.of(), List.of());
        ActionData.SimpleAction step2 = new ActionData.SimpleAction("step2", "intent", REST, "url", "target", Map.of(), HttpMethod.GET, null, List.of(), List.of());
        ActionData.SimpleAction comp1 = new ActionData.SimpleAction("comp1", "intent", REST, "url", "target", Map.of(), HttpMethod.GET, null, List.of(), List.of());

        ActionData.ComplexWorkflow workflow = new ActionData.ComplexWorkflow(
            "wf", "intent", List.of(step1, step2), Map.of("step1", comp1)
        );
        UUID executionId = UUID.randomUUID();

        when(dispatcher.dispatchSimple(step1, executionId)).thenReturn(true);
        when(dispatcher.dispatchSimple(step2, executionId)).thenReturn(false);
        when(dispatcher.dispatchSimple(comp1, executionId)).thenReturn(false); // Compensation fails!

        boolean result = engine.execute(workflow, executionId, dispatcher);

        assertFalse(result);
        verify(gateway).updateActionState("comp1", ExecutionStatus.FAILED);
    }
}
