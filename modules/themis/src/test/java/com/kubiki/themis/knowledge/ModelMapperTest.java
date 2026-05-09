package com.kubiki.themis.knowledge;

import com.kubiki.themis.model.ActionData;
import com.kubiki.themis.model.Protocol;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.impl.MapBindingSet;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ModelMapperTest {
    private final ModelMapper mapper = new ModelMapper();
    private final ValueFactory vf = SimpleValueFactory.getInstance();

    @Test
    void shouldMapSimpleAction() {
        IRI actionId = vf.createIRI("http://example.org/action1");
        MapBindingSet bs = new MapBindingSet();
        bs.addBinding("action", actionId);
        bs.addBinding("intent", vf.createIRI("http://example.org/SimpleAction"));
        bs.addBinding("protocol", vf.createIRI("http://example.org/REST"));
        bs.addBinding("target", vf.createIRI("http://example.org/target1"));
        bs.addBinding("instruction", vf.createLiteral("Do something"));
        bs.addBinding("method", vf.createLiteral("POST"));
        bs.addBinding("payload", vf.createLiteral("{}"));

        Result<ActionData> result = mapper.mapAction(actionId, Map.of(actionId, List.of(bs)));

        assertThat(result.isSuccess()).isTrue();
        ActionData.SimpleAction action = (ActionData.SimpleAction) result.value();
        assertThat(action.id().stringValue()).isEqualTo("http://example.org/action1");
        assertThat(action.protocol()).isEqualTo(Protocol.REST);
        assertThat(action.method()).isEqualTo(HttpMethod.POST);
        assertThat(action.instruction()).isEqualTo("Do something");
        assertThat(action.payload()).isEqualTo("{}");
    }

    @Test
    void shouldMapSimpleActionWithConditions() {
        IRI actionId = vf.createIRI("http://example.org/action1");
        MapBindingSet bs1 = new MapBindingSet();
        bs1.addBinding("action", actionId);
        bs1.addBinding("intent", vf.createIRI("http://example.org/SimpleAction"));
        bs1.addBinding("protocol", vf.createIRI("http://example.org/REST"));
        bs1.addBinding("target", vf.createIRI("http://example.org/target1"));
        bs1.addBinding("preId", vf.createIRI("http://example.org/pre1"));
        bs1.addBinding("preType", vf.createIRI("http://example.org/PreType"));
        bs1.addBinding("prePolicy", vf.createLiteral("prePolicy1"));

        MapBindingSet bs2 = new MapBindingSet();
        bs2.addBinding("action", actionId);
        bs2.addBinding("intent", vf.createIRI("http://example.org/SimpleAction"));
        bs2.addBinding("protocol", vf.createIRI("http://example.org/REST"));
        bs2.addBinding("target", vf.createIRI("http://example.org/target1"));
        bs2.addBinding("postId", vf.createIRI("http://example.org/post1"));
        bs2.addBinding("postType", vf.createIRI("http://example.org/PostType"));
        bs2.addBinding("postPolicy", vf.createLiteral("postPolicy1"));

        Result<ActionData> result = mapper.mapAction(actionId, Map.of(actionId, List.of(bs1, bs2)));

        assertThat(result.isSuccess()).isTrue();
        ActionData.SimpleAction action = (ActionData.SimpleAction) result.value();
        assertThat(action.preConditions()).hasSize(1);
        assertThat(action.preConditions().get(0).id().stringValue()).isEqualTo("http://example.org/pre1");
        assertThat(action.postConditions()).hasSize(1);
        assertThat(action.postConditions().get(0).id().stringValue()).isEqualTo("http://example.org/post1");
    }

    @Test
    void shouldMapComplexWorkflow() {
        IRI workflowId = vf.createIRI("http://example.org/workflow");
        IRI stepId = vf.createIRI("http://example.org/step1");

        MapBindingSet wbs = new MapBindingSet();
        wbs.addBinding("action", workflowId);
        wbs.addBinding("intent", vf.createIRI("http://example.org/ComplexWorkflow"));
        wbs.addBinding("step", stepId);

        MapBindingSet sbs = new MapBindingSet();
        sbs.addBinding("action", stepId);
        sbs.addBinding("intent", vf.createIRI("http://example.org/SimpleAction"));
        sbs.addBinding("protocol", vf.createIRI("http://example.org/REST"));
        sbs.addBinding("target", vf.createIRI("http://example.org/target1"));

        Map<IRI, List<BindingSet>> allBindings = Map.of(
                workflowId, List.of(wbs),
                stepId, List.of(sbs)
        );

        Result<ActionData> result = mapper.mapAction(workflowId, allBindings);

        assertThat(result.isSuccess()).isTrue();
        ActionData.ComplexWorkflow workflow = (ActionData.ComplexWorkflow) result.value();
        assertThat(workflow.steps()).hasSize(1);
        assertThat(workflow.steps().get(0).id()).isEqualTo(stepId);
    }

    @Test
    void shouldReturnFailureWhenIntentIsMissing() {
        IRI actionId = vf.createIRI("http://example.org/action1");
        MapBindingSet bs = new MapBindingSet();
        bs.addBinding("action", actionId);

        Result<ActionData> result = mapper.mapAction(actionId, Map.of(actionId, List.of(bs)));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.error()).contains("Missing or invalid IRI binding: intent");
    }

    @Test
    void shouldReturnFailureWhenTargetIsMissingInSimpleAction() {
        IRI actionId = vf.createIRI("http://example.org/action1");
        MapBindingSet bs = new MapBindingSet();
        bs.addBinding("action", actionId);
        bs.addBinding("intent", vf.createIRI("http://example.org/SimpleAction"));

        Result<ActionData> result = mapper.mapAction(actionId, Map.of(actionId, List.of(bs)));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.error()).contains("Missing or invalid IRI binding: target");
    }
}
