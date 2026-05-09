package com.kubiki.themis.knowledge;

import com.kubiki.themis.constants.OntologyConstants;
import com.kubiki.themis.model.ActionData;
import com.kubiki.themis.model.Protocol;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.impl.MapBindingSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MoaMapperTest {

    private MoamMapper mapper;
    private ValueFactory vf = SimpleValueFactory.getInstance();
    private String ns = "http://moa#";

    @BeforeEach
    void setUp() {
        mapper = new MoamMapper();
    }

    @Test
    void shouldMapSimpleAction() {
        MapBindingSet bs = new MapBindingSet();
        IRI actionIri = vf.createIRI(ns + "action1");
        IRI intentIri = vf.createIRI(ns + "RestartAction");
        IRI targetIri = vf.createIRI(ns + "pod1");
        
        bs.addBinding("action", actionIri);
        bs.addBinding("intent", intentIri);
        bs.addBinding("target", targetIri);
        bs.addBinding("protocol", vf.createLiteral("REST"));
        bs.addBinding("method", vf.createLiteral("POST"));
        bs.addBinding("instruction", vf.createLiteral("http://restart"));

        ActionData.SimpleAction action = mapper.mapSimpleActionGroup(List.of(bs));

        assertNotNull(action);
        assertEquals(actionIri, action.id());
        assertEquals(ns + "RestartAction", action.functionalIntent());
        assertEquals(targetIri, action.targetIri());
        assertEquals(Protocol.REST, action.protocol());
        assertEquals(HttpMethod.POST, action.method());
    }

    @Test
    void shouldMapComplexWorkflow() {
        IRI wfIri = vf.createIRI(ns + "wf1");
        IRI intentIri = vf.createIRI(ns + OntologyConstants.CLASS_COMPLEX_WORKFLOW);
        IRI step1Iri = vf.createIRI(ns + "step1");
        IRI comp1Iri = vf.createIRI(ns + "comp1");

        MapBindingSet bs1 = new MapBindingSet();
        bs1.addBinding("action", wfIri);
        bs1.addBinding("intent", intentIri);
        bs1.addBinding("step", step1Iri);
        bs1.addBinding("compensation", comp1Iri);

        // Mock bindings for the steps
        MapBindingSet bsStep1 = new MapBindingSet();
        bsStep1.addBinding("action", step1Iri);
        bsStep1.addBinding("intent", vf.createIRI(ns + "StepAction"));
        bsStep1.addBinding("target", vf.createIRI(ns + "res1"));
        bsStep1.addBinding("protocol", vf.createLiteral("SHELL"));

        MapBindingSet bsComp1 = new MapBindingSet();
        bsComp1.addBinding("action", comp1Iri);
        bsComp1.addBinding("intent", vf.createIRI(ns + "CompAction"));
        bsComp1.addBinding("target", vf.createIRI(ns + "res1"));
        bsComp1.addBinding("protocol", vf.createLiteral("REST"));

        Map<IRI, List<BindingSet>> allBindings = Map.of(
                wfIri, List.of(bs1),
                step1Iri, List.of(bsStep1),
                comp1Iri, List.of(bsComp1)
        );

        ActionData action = mapper.mapAction(wfIri, allBindings);

        assertInstanceOf(ActionData.ComplexWorkflow.class, action);
        ActionData.ComplexWorkflow wf = (ActionData.ComplexWorkflow) action;
        assertEquals(1, wf.steps().size());
        assertEquals(1, wf.compensations().size());
        assertEquals(step1Iri, wf.steps().get(0).id());
        assertEquals(comp1Iri, wf.compensations().get(step1Iri).id());
    }
}
