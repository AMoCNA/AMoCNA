package com.kubiki.palamedes.knowledge;

import com.kubiki.common.model.Protocol;
import com.kubiki.palamedes.model.ActionData;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.impl.MapBindingSet;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelMapperTest {
    private final ModelMapper mapper = new ModelMapper();
    private final ValueFactory vf = SimpleValueFactory.getInstance();

    @Test
    void shouldMapSimpleAction() {
        IRI actionId = vf.createIRI("http://test/action1");
        MapBindingSet bs = new MapBindingSet();
        bs.addBinding("intent", vf.createIRI("http://test/RestartAction"));
        bs.addBinding("target", vf.createIRI("http://test/pod1"));
        bs.addBinding("protocol", vf.createLiteral("REST"));
        bs.addBinding("instruction", vf.createLiteral("http://restart"));
        bs.addBinding("method", vf.createLiteral("POST"));
        bs.addBinding("expectedStatusCode", vf.createLiteral("201"));
        bs.addBinding("timeoutSeconds", vf.createLiteral("45"));
        bs.addBinding("maxRetries", vf.createLiteral("5"));
        bs.addBinding("isIdempotent", vf.createLiteral("true"));

        Result<ActionData> result = mapper.mapAction(actionId, Map.of(actionId, List.of(bs)));

        assertTrue(result.isSuccess());
        ActionData.SimpleAction action = (ActionData.SimpleAction) result.value();
        assertEquals(Protocol.REST, action.protocol());
        assertEquals(HttpMethod.POST, action.method());
        assertEquals(201, action.expectedStatusCode());
        assertEquals(45, action.timeoutSeconds());
        assertEquals(5, action.maxRetries());
        assertTrue(action.isIdempotent());
    }

    @Test
    void shouldMapMultipleActions() {
        IRI action1 = vf.createIRI("http://test/action1");
        IRI action2 = vf.createIRI("http://test/action2");

        MapBindingSet bs1 = new MapBindingSet();
        bs1.addBinding("intent", vf.createIRI("http://test/RestartAction"));
        bs1.addBinding("target", vf.createIRI("http://test/pod1"));
        bs1.addBinding("protocol", vf.createLiteral("REST"));
        bs1.addBinding("instruction", vf.createLiteral("http://restart"));

        MapBindingSet bs2 = new MapBindingSet();
        bs2.addBinding("intent", vf.createIRI("http://test/StopAction"));
        bs2.addBinding("target", vf.createIRI("http://test/pod2"));
        bs2.addBinding("protocol", vf.createLiteral("SHELL"));
        bs2.addBinding("instruction", vf.createLiteral("stop.sh"));

        Map<IRI, List<BindingSet>> allBindings = Map.of(
                action1, List.of(bs1),
                action2, List.of(bs2)
        );

        Map<IRI, ActionData> results = mapper.mapActions(allBindings, List.of(action1, action2));

        assertEquals(2, results.size());
        assertTrue(results.containsKey(action1));
        assertTrue(results.containsKey(action2));
        assertEquals(Protocol.REST, ((ActionData.SimpleAction) results.get(action1)).protocol());
        assertEquals(Protocol.SHELL, ((ActionData.SimpleAction) results.get(action2)).protocol());
    }

    @Test
    void shouldMapComplexWorkflowsInBulk() {
        IRI workflowId = vf.createIRI("http://test/workflow");
        IRI stepId = vf.createIRI("http://test/step1");
        IRI standaloneId = vf.createIRI("http://test/standalone");

        // Workflow bindings
        MapBindingSet wbs = new MapBindingSet();
        wbs.addBinding("intent", vf.createIRI("http://test/ComplexWorkflow"));
        wbs.addBinding("target", vf.createIRI("http://test/cluster"));
        wbs.addBinding("step", stepId);

        // Step bindings
        MapBindingSet sbs = new MapBindingSet();
        sbs.addBinding("intent", vf.createIRI("http://test/RestartAction"));
        sbs.addBinding("target", vf.createIRI("http://test/pod1"));
        sbs.addBinding("protocol", vf.createLiteral("REST"));
        sbs.addBinding("instruction", vf.createLiteral("http://restart"));

        // Standalone bindings
        MapBindingSet stbs = new MapBindingSet();
        stbs.addBinding("intent", vf.createIRI("http://test/StopAction"));
        stbs.addBinding("target", vf.createIRI("http://test/pod2"));
        stbs.addBinding("protocol", vf.createLiteral("SHELL"));
        stbs.addBinding("instruction", vf.createLiteral("stop.sh"));

        Map<IRI, List<BindingSet>> allBindings = Map.of(
                workflowId, List.of(wbs),
                stepId, List.of(sbs),
                standaloneId, List.of(stbs)
        );

        Map<IRI, ActionData> results = mapper.mapActions(allBindings, List.of(workflowId, standaloneId));

        assertEquals(2, results.size());

        ActionData.ComplexWorkflow workflow = (ActionData.ComplexWorkflow) results.get(workflowId);
        assertEquals(1, workflow.steps().size());
        assertEquals(stepId, workflow.steps().get(0).id());

        ActionData.SimpleAction standalone = (ActionData.SimpleAction) results.get(standaloneId);
        assertEquals(standaloneId, standalone.id());
    }
}
