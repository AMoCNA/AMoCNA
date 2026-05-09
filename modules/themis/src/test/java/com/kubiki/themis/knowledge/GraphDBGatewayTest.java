package com.kubiki.themis.knowledge;

import com.kubiki.themis.config.ThemisProperties;
import com.kubiki.themis.model.ExecutionStatus;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.sail.memory.MemoryStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GraphDBGatewayTest {
    private static final String MOA_NS = "http://moa#";
    private Repository repository;
    private GraphDBGateway gateway;
    private ThemisProperties properties;
    private MoaMapper moaMapper;

    @BeforeEach
    void setUp() {
        repository = new SailRepository(new MemoryStore());
        repository.init();

        properties = Mockito.mock(ThemisProperties.class);
        ThemisProperties.GraphDB graphDB = new ThemisProperties.GraphDB("http://localhost:7200", "amocna", 5000);
        ThemisProperties.Ontology ontology = new ThemisProperties.Ontology(MOA_NS);

        when(properties.graphdb()).thenReturn(graphDB);
        when(properties.ontology()).thenReturn(ontology);

        moaMapper = new MoaMapper();

        gateway = new GraphDBGateway(properties, moaMapper) {
            private final Repository testRepo = repository;

            @Override
            public void init() {
            } // Already init in setUp

            @Override
            protected Repository getRepository() {
                return testRepo;
            }
        };
    }

    @AfterEach
    void tearDown() {
        repository.shutDown();
    }

    @Test
    void shouldUpdateActionState() {
        ValueFactory vf = repository.getValueFactory();
        IRI actionIri = vf.createIRI(MOA_NS + "action1");
        IRI hasExecutionStatus = vf.createIRI(MOA_NS + "hasExecutionStatus");

        try (RepositoryConnection conn = repository.getConnection()) {
            conn.add(actionIri, RDF.TYPE, vf.createIRI(MOA_NS + "AutonomicAction"));
        }

        gateway.updateActionState(MOA_NS + "action1", ExecutionStatus.SUCCESS);

        try (RepositoryConnection conn = repository.getConnection()) {
            Literal status = (Literal) conn.getStatements(actionIri, hasExecutionStatus, null).next().getObject();
            assertEquals("SUCCESS", status.getLabel());
        }
    }

    @Test
    void updateActionStateShouldThrowOnPersistenceFailure() {
        Repository mockedRepo = mock(Repository.class);
        when(mockedRepo.getValueFactory()).thenReturn(SimpleValueFactory.getInstance());
        when(mockedRepo.getConnection()).thenThrow(new RuntimeException("DB Down"));

        GraphDBGateway gatewayWithMock = new GraphDBGateway(properties, moaMapper) {
            @Override
            protected Repository getRepository() {
                return mockedRepo;
            }
        };

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> gatewayWithMock.updateActionState(MOA_NS + "action1", ExecutionStatus.SUCCESS));
        assertEquals("Persistence failure", ex.getMessage());
    }

    @Test
    void fetchActionStructureShouldReturnNullOnException() {
        Repository mockedRepo = mock(Repository.class);
        when(mockedRepo.getConnection()).thenThrow(new RuntimeException("Query Failed"));

        GraphDBGateway gatewayWithMock = new GraphDBGateway(properties, moaMapper) {
            @Override
            protected Repository getRepository() {
                return mockedRepo;
            }
        };

        assertNull(gatewayWithMock.fetchActionStructure(MOA_NS + "action1"));
    }

    @Test
    void findActionsForResourceShouldReturnEmptyListOnException() {
        Repository mockedRepo = mock(Repository.class);
        when(mockedRepo.getConnection()).thenThrow(new RuntimeException("Query Failed"));

        GraphDBGateway gatewayWithMock = new GraphDBGateway(properties, moaMapper) {
            @Override
            protected Repository getRepository() {
                return mockedRepo;
            }
        };

        assertTrue(gatewayWithMock.findActionsForResource("res1").isEmpty());
    }

    @Test
    void executeConditionQueryShouldReturnFalseOnException() {
        Repository mockedRepo = mock(Repository.class);
        when(mockedRepo.getConnection()).thenThrow(new RuntimeException("Query Failed"));

        GraphDBGateway gatewayWithMock = new GraphDBGateway(properties, moaMapper) {
            @Override
            protected Repository getRepository() {
                return mockedRepo;
            }
        };

        assertFalse(gatewayWithMock.executeConditionQuery("ASK { ?s ?p ?o }"));
    }

    @Test
    void executeConditionQueryShouldReturnTrue() {
        ValueFactory vf = repository.getValueFactory();
        try (RepositoryConnection conn = repository.getConnection()) {
            conn.add(vf.createIRI(MOA_NS + "s"), vf.createIRI(MOA_NS + "p"), vf.createIRI(MOA_NS + "o"));
        }

        assertTrue(gateway.executeConditionQuery("ASK { ?s ?p ?o }"));
    }

    @Test
    void executeConditionQueryShouldReturnFalseWhenNoMatch() {
        assertFalse(gateway.executeConditionQuery("ASK { <http://none> <http://none> <http://none> }"));
    }
}
