package com.kubiki.themis.knowledge;

import com.kubiki.themis.model.ActionData;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.impl.MapBindingSet;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MoaMapperTest {

    @Test
    void mapsComplexWorkflowWithStepsAndCompensations() {
        MoaMapper mapper = new MoaMapper();
        SimpleValueFactory vf = SimpleValueFactory.getInstance();

        MapBindingSet mainAction = new MapBindingSet();
        mainAction.addBinding("action", vf.createIRI("http://moa#complex1"));
        mainAction.addBinding("intent", vf.createIRI("http://moa#ComplexWorkflow"));
        mainAction.addBinding("step", vf.createIRI("http://moa#step1"));
        mainAction.addBinding("compensation", vf.createIRI("http://moa#comp1"));

        MapBindingSet stepAction = new MapBindingSet();
        stepAction.addBinding("action", vf.createIRI("http://moa#step1"));
        stepAction.addBinding("intent", vf.createIRI("http://moa#SimpleAction"));
        stepAction.addBinding("target", vf.createIRI("http://target1"));

        MapBindingSet compAction = new MapBindingSet();
        compAction.addBinding("action", vf.createIRI("http://moa#comp1"));
        compAction.addBinding("intent", vf.createIRI("http://moa#SimpleAction"));
        compAction.addBinding("target", vf.createIRI("http://target1"));

        Map<String, List<BindingSet>> allBindings = Map.of(
                "http://moa#complex1", List.of(mainAction),
                "http://moa#step1", List.of(stepAction),
                "http://moa#comp1", List.of(compAction)
        );

        ActionData result = mapper.mapAction("http://moa#complex1", allBindings);

        assertInstanceOf(ActionData.ComplexWorkflow.class, result);
        ActionData.ComplexWorkflow workflow = (ActionData.ComplexWorkflow) result;
        assertEquals(1, workflow.steps().size());
        assertEquals("http://moa#step1", workflow.steps().get(0).id());
        assertTrue(workflow.compensations().containsKey("http://moa#step1"));
        assertEquals("http://moa#comp1", workflow.compensations().get("http://moa#step1").id());
    }
}
