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

    @Test
    void mapActionReturnsNullForEmptyBindings() {
        MoaMapper mapper = new MoaMapper();
        assertNull(mapper.mapAction("id", Map.of()));
    }

    @Test
    void mapSimpleActionGroupReturnsNullForEmptyBindings() {
        MoaMapper mapper = new MoaMapper();
        assertNull(mapper.mapSimpleActionGroup(List.of()));
    }

    @Test
    void mapSimpleActionWithEdgeData() {
        MoaMapper mapper = new MoaMapper();
        SimpleValueFactory vf = SimpleValueFactory.getInstance();

        MapBindingSet bs = new MapBindingSet();
        bs.addBinding("action", vf.createIRI("http://moa#simple"));
        bs.addBinding("intent", vf.createIRI("http://moa#SimpleAction"));
        bs.addBinding("target", vf.createIRI("http://target"));
        bs.addBinding("protocol", vf.createLiteral("shell"));
        bs.addBinding("method", vf.createLiteral("post"));
        bs.addBinding("preId", vf.createLiteral("pre1"));
        // missing preType and prePolicy
        bs.addBinding("postId", vf.createLiteral("post1"));
        bs.addBinding("postType", vf.createLiteral("type1"));
        bs.addBinding("postPolicy", vf.createLiteral("policy1"));

        ActionData.SimpleAction result = mapper.mapSimpleAction(bs);

        assertEquals("http://moa#simple", result.id());
        assertEquals(com.kubiki.themis.model.Protocol.SHELL, result.protocol());
        assertEquals(org.springframework.http.HttpMethod.POST, result.method());
        assertEquals(1, result.preConditions().size());
        assertNull(result.preConditions().get(0).type());
        assertEquals(1, result.postConditions().size());
        assertEquals("type1", result.postConditions().get(0).type());
    }

    @Test
    void mapActionHandlesUnknownProtocolGracefully() {
        MoaMapper mapper = new MoaMapper();
        SimpleValueFactory vf = SimpleValueFactory.getInstance();

        MapBindingSet bs = new MapBindingSet();
        bs.addBinding("action", vf.createIRI("http://moa#simple"));
        bs.addBinding("intent", vf.createIRI("http://moa#SimpleAction"));
        bs.addBinding("target", vf.createIRI("http://target"));
        bs.addBinding("protocol", vf.createLiteral("UNKNOWN"));

        assertThrows(IllegalArgumentException.class, () -> mapper.mapSimpleAction(bs));
    }

    @Test
    void mapActionWithUnknownIntentFallbackToSimple() {
        MoaMapper mapper = new MoaMapper();
        SimpleValueFactory vf = SimpleValueFactory.getInstance();

        MapBindingSet bs = new MapBindingSet();
        bs.addBinding("action", vf.createIRI("http://moa#unknown"));
        bs.addBinding("intent", vf.createIRI("http://moa#UnknownIntent"));
        bs.addBinding("target", vf.createIRI("http://target"));

        ActionData result = mapper.mapAction("http://moa#unknown", Map.of("http://moa#unknown", List.of(bs)));

        assertInstanceOf(ActionData.SimpleAction.class, result);
        assertEquals("http://moa#unknown", result.id());
    }

    @Test
    void mapActionHandlesRecursiveMappingDepth() {
        MoaMapper mapper = new MoaMapper();
        SimpleValueFactory vf = SimpleValueFactory.getInstance();

        // A (Complex) -> B (Complex) -> C (Simple)
        MapBindingSet actionA = new MapBindingSet();
        actionA.addBinding("action", vf.createIRI("http://moa#A"));
        actionA.addBinding("intent", vf.createIRI("http://moa#ComplexWorkflow"));
        actionA.addBinding("step", vf.createIRI("http://moa#B"));

        MapBindingSet actionB = new MapBindingSet();
        actionB.addBinding("action", vf.createIRI("http://moa#B"));
        actionB.addBinding("intent", vf.createIRI("http://moa#ComplexWorkflow"));
        actionB.addBinding("step", vf.createIRI("http://moa#C"));

        MapBindingSet actionC = new MapBindingSet();
        actionC.addBinding("action", vf.createIRI("http://moa#C"));
        actionC.addBinding("intent", vf.createIRI("http://moa#SimpleAction"));
        actionC.addBinding("target", vf.createIRI("http://targetC"));

        ActionData result = mapper.mapAction("http://moa#A", Map.of(
                "http://moa#A", List.of(actionA),
                "http://moa#B", List.of(actionB),
                "http://moa#C", List.of(actionC)
        ));

        assertInstanceOf(ActionData.ComplexWorkflow.class, result);
        ActionData.ComplexWorkflow wfA = (ActionData.ComplexWorkflow) result;
        assertEquals(1, wfA.steps().size());

        ActionData stepB = wfA.steps().get(0);
        assertInstanceOf(ActionData.ComplexWorkflow.class, stepB);
        ActionData.ComplexWorkflow wfB = (ActionData.ComplexWorkflow) stepB;
        assertEquals(1, wfB.steps().size());

        ActionData stepC = wfB.steps().get(0);
        assertInstanceOf(ActionData.SimpleAction.class, stepC);
    }

    @Test
    void mapActionAvoidsImmediateSelfLoop() {
        MoaMapper mapper = new MoaMapper();
        SimpleValueFactory vf = SimpleValueFactory.getInstance();

        MapBindingSet actionA = new MapBindingSet();
        actionA.addBinding("action", vf.createIRI("http://moa#A"));
        actionA.addBinding("intent", vf.createIRI("http://moa#ComplexWorkflow"));
        actionA.addBinding("step", vf.createIRI("http://moa#A")); // Self-referencing step

        ActionData result = mapper.mapAction("http://moa#A", Map.of("http://moa#A", List.of(actionA)));

        assertInstanceOf(ActionData.ComplexWorkflow.class, result);
        assertTrue(((ActionData.ComplexWorkflow) result).steps().isEmpty());
    }

    @Test
    void mapSimpleActionGroupReturnsNullForNull() {
        MoaMapper mapper = new MoaMapper();
        assertNull(mapper.mapSimpleActionGroup(null));
    }
}
