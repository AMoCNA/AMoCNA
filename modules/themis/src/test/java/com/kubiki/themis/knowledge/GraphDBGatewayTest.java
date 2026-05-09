package com.kubiki.themis.knowledge;

import com.kubiki.themis.config.ThemisProperties;
import com.kubiki.themis.model.ExecutionStatus;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.sail.memory.MemoryStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.io.DefaultResourceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@DisplayName("GraphDBGateway Tests")
class GraphDBGatewayTest {
    private Repository repository;
    private GraphDBGateway gateway;
    private SparqlClient sparqlClient;
    private SparqlQueryBuilder sparqlQueryBuilder;
    private ModelMapper modelMapper;
    private OntologyRegistry ontologyRegistry;
    private SparqlLoader sparqlLoader;
    private ThemisProperties properties;

    private static final String MOA_NS = "http://moa#";

    @BeforeEach
    void setUp() {
        repository = new SailRepository(new MemoryStore());
        repository.init();

        properties = Mockito.mock(ThemisProperties.class);
        ThemisProperties.Ontology ontology = new ThemisProperties.Ontology(MOA_NS);
        when(properties.ontology()).thenReturn(ontology);

        ontologyRegistry = new OntologyRegistry(properties);
        sparqlClient = new SparqlClient(repository);
        sparqlLoader = new SparqlLoader(new DefaultResourceLoader());
        sparqlQueryBuilder = new SparqlQueryBuilder(sparqlLoader, ontologyRegistry);
        modelMapper = new ModelMapper();

        gateway = new GraphDBGateway(sparqlClient, sparqlQueryBuilder, modelMapper, ontologyRegistry);
    }

    @AfterEach
    void tearDown() {
        repository.shutDown();
    }

    @Test
    @DisplayName("Should update action state in GraphDB")
    void shouldUpdateActionState() {
        ValueFactory vf = repository.getValueFactory();
        IRI actionIri = vf.createIRI(MOA_NS + "action1");
        IRI hasExecutionStatus = vf.createIRI(MOA_NS + "hasExecutionStatus");

        try (RepositoryConnection conn = repository.getConnection()) {
            conn.add(actionIri, RDF.TYPE, vf.createIRI(MOA_NS + "AutonomicAction"));
        }

        gateway.updateActionState(actionIri, ExecutionStatus.SUCCESS);

        try (RepositoryConnection conn = repository.getConnection()) {
            Literal status = (Literal) conn.getStatements(actionIri, hasExecutionStatus, null).next().getObject();
            assertEquals("SUCCESS", status.getLabel());
        }
    }

    @Test
    @DisplayName("Should find actions for resource")
    void shouldFindActionsForResource() {
        ValueFactory vf = repository.getValueFactory();
        IRI actionIri = vf.createIRI(MOA_NS + "action1");
        IRI resourceIri = vf.createIRI(MOA_NS + "resource1");
        IRI intent = vf.createIRI(MOA_NS + "RestartAction");

        try (RepositoryConnection conn = repository.getConnection()) {
            conn.add(actionIri, RDF.TYPE, intent);
            conn.add(actionIri, vf.createIRI(MOA_NS + "targetsEntity"), resourceIri);
            conn.add(actionIri, vf.createIRI(MOA_NS + "hasExecutionProtocol"), vf.createIRI(MOA_NS + "REST"));
        }

        java.util.List<com.kubiki.themis.model.ActionData.SimpleAction> actions = gateway.findActionsForResource(resourceIri);
        
        assertEquals(1, actions.size());
        assertEquals(actionIri, actions.get(0).id());
        assertEquals(intent.stringValue(), actions.get(0).functionalIntent());
    }

    @Test
    @DisplayName("Should fetch action structure")
    void shouldFetchActionStructure() {
        ValueFactory vf = repository.getValueFactory();
        IRI actionIri = vf.createIRI(MOA_NS + "action1");
        IRI intent = vf.createIRI(MOA_NS + "RestartAction");
        IRI resourceIri = vf.createIRI(MOA_NS + "resource1");

        try (RepositoryConnection conn = repository.getConnection()) {
            conn.add(actionIri, RDF.TYPE, intent);
            conn.add(actionIri, vf.createIRI(MOA_NS + "targetsEntity"), resourceIri);
            conn.add(actionIri, vf.createIRI(MOA_NS + "hasExecutionProtocol"), vf.createIRI(MOA_NS + "REST"));
            conn.add(actionIri, vf.createIRI(MOA_NS + "hasExecutionInstruction"), vf.createLiteral("restart"));
        }

        com.kubiki.themis.model.ActionData action = gateway.fetchActionStructure(actionIri);
        
        assertEquals(actionIri, action.id());
        assertEquals(intent.stringValue(), action.functionalIntent());
    }
}
