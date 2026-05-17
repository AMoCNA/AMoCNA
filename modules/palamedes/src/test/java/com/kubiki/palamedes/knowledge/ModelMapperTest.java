package com.kubiki.palamedes.knowledge;

import com.kubiki.palamedes.model.ActionData;
import com.kubiki.palamedes.model.Protocol;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
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
}
