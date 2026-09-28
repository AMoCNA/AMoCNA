package com.kubiki.palamedes.knowledge;

import com.kubiki.palamedes.model.ActionData;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.sail.memory.MemoryStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class GraphDBGatewayBulkTest {

    private static Repository inMemoryRepo;
    private final ValueFactory vf = SimpleValueFactory.getInstance();
    @Autowired
    private GraphDBGateway gateway;
    @Autowired
    private com.kubiki.common.ontology.OntologyRegistry registry;
    @MockitoBean
    private Repository realRepository;

    @BeforeEach
    void setUp() {
        if (inMemoryRepo == null) {
            inMemoryRepo = new SailRepository(new MemoryStore());
            inMemoryRepo.init();
        }
        clearGraph();

        try {
            when(realRepository.getConnection()).thenAnswer(inv -> inMemoryRepo.getConnection());
            when(realRepository.getValueFactory()).thenReturn(vf);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void clearGraph() {
        try (RepositoryConnection conn = inMemoryRepo.getConnection()) {
            conn.begin();
            conn.clear();
            conn.commit();
        }
    }

    @Test
    void shouldFetchMultipleActionStructures() {
        IRI action1 = vf.createIRI("http://test/action1");
        IRI action2 = vf.createIRI("http://test/action2");
        IRI intent1 = vf.createIRI("http://test/Intent1");
        IRI intent2 = vf.createIRI("http://test/Intent2");
        IRI target = vf.createIRI("http://test/target1");

        try (RepositoryConnection conn = inMemoryRepo.getConnection()) {
            conn.begin();
            // Action 1
            conn.add(action1, RDF.TYPE, intent1);
            conn.add(action1, RDF.TYPE, registry.actionsOntology("SimpleAction"));
            conn.add(action1, registry.actionsOntology("targetsEntity"), target);
            conn.add(intent1, registry.actionsOntology("hasExecutionProtocol"), vf.createLiteral("REST"));
            conn.add(intent1, registry.actionsOntology("hasExecutionInstruction"), vf.createLiteral("http://exec1"));

            // Action 2
            conn.add(action2, RDF.TYPE, intent2);
            conn.add(action2, RDF.TYPE, registry.actionsOntology("SimpleAction"));
            conn.add(action2, registry.actionsOntology("targetsEntity"), target);
            conn.add(intent2, registry.actionsOntology("hasExecutionProtocol"), vf.createLiteral("SHELL"));
            conn.add(intent2, registry.actionsOntology("hasExecutionInstruction"), vf.createLiteral("exec2.sh"));
            conn.commit();
        }

        Map<IRI, ActionData> results = gateway.fetchActionStructures(List.of(action1, action2));

        assertEquals(2, results.size());
        assertTrue(results.containsKey(action1));
        assertTrue(results.containsKey(action2));

        ActionData.SimpleAction sa1 = (ActionData.SimpleAction) results.get(action1);
        assertEquals("REST", sa1.protocol().name());

        ActionData.SimpleAction sa2 = (ActionData.SimpleAction) results.get(action2);
        assertEquals("SHELL", sa2.protocol().name());
    }
}
