package com.kubiki.themis.knowledge;

import com.kubiki.themis.model.ActionData;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.impl.MapBindingSet;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MoaMapperTest {
    @Test
    void shouldMapGroupedBindingsToSimpleActionWithConditions() {
        MoaMapper mapper = new MoaMapper();
        SimpleValueFactory vf = SimpleValueFactory.getInstance();
        
        MapBindingSet bs1 = new MapBindingSet();
        bs1.addBinding("action", vf.createIRI("http://test/action1"));
        bs1.addBinding("intent", vf.createIRI("http://test/Intent"));
        bs1.addBinding("target", vf.createIRI("http://test/target"));
        bs1.addBinding("preId", vf.createIRI("http://test/pre1"));
        bs1.addBinding("preType", vf.createIRI("http://test/PreType"));
        bs1.addBinding("prePolicy", vf.createLiteral("ASK { ?s ?p ?o }"));
        
        MapBindingSet bs2 = new MapBindingSet();
        bs2.addBinding("action", vf.createIRI("http://test/action1"));
        bs2.addBinding("intent", vf.createIRI("http://test/Intent"));
        bs2.addBinding("target", vf.createIRI("http://test/target"));
        bs2.addBinding("postId", vf.createIRI("http://test/post1"));
        bs2.addBinding("postType", vf.createIRI("http://test/PostType"));
        bs2.addBinding("postPolicy", vf.createLiteral("ASK { ?a ?b ?c }"));

        ActionData.SimpleAction result = mapper.mapSimpleActionGroup(List.of(bs1, bs2));

        assertEquals("http://test/action1", result.id());
        assertEquals(1, result.preConditions().size());
        assertEquals("http://test/pre1", result.preConditions().get(0).id());
        assertEquals("http://test/PreType", result.preConditions().get(0).type());
        assertEquals("ASK { ?s ?p ?o }", result.preConditions().get(0).policy());
        
        assertEquals(1, result.postConditions().size());
        assertEquals("http://test/post1", result.postConditions().get(0).id());
        assertEquals("http://test/PostType", result.postConditions().get(0).type());
        assertEquals("ASK { ?a ?b ?c }", result.postConditions().get(0).policy());
    }
}
