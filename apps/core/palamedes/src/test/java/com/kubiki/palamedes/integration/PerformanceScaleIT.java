package com.kubiki.palamedes.integration;

import com.kubiki.common.ontology.OntologyRegistry;
import com.kubiki.palamedes.knowledge.GraphDBGateway;
import com.kubiki.palamedes.model.ActiveActionSummary;
import com.kubiki.palamedes.pipeline.MapePipeline;
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
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "palamedes.engine.batch-size=50",
        "palamedes.engine.fallback-pipeline-rate-ms=100000"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class PerformanceScaleIT {

    private static Repository inMemoryRepo;
    private final ValueFactory vf = SimpleValueFactory.getInstance();
    @Autowired
    private MapePipeline mapePipeline;
    @Autowired
    private GraphDBGateway gateway;
    @Autowired
    private OntologyRegistry registry;
    @MockitoSpyBean
    private com.kubiki.palamedes.knowledge.SparqlRepository sparqlRepository;
    @MockitoBean
    private RabbitTemplate rabbitTemplate;
    @MockitoBean
    private Repository realRepository;

    @Autowired
    private com.kubiki.palamedes.dispatcher.OutboxService outboxService;

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
    void shouldHandleLargeScaleWithBatching() {
        int actionCount = 200;
        IRI actionType = registry.actionsOntology("SimpleAction");
        IRI hasCurrentState = registry.actionsOntology("hasCurrentState");
        IRI stateValidated = registry.actionsOntology("State_Validated");
        IRI targetsEntity = registry.actionsOntology("targetsEntity");
        IRI hasActionID = registry.actionsOntology("hasActionID");
        IRI protocol = registry.actionsOntology("hasExecutionProtocol");
        IRI instruction = registry.actionsOntology("hasExecutionInstruction");
        IRI statusCode = registry.actionsOntology("hasExpectedStatusCode");

        try (RepositoryConnection conn = inMemoryRepo.getConnection()) {
            conn.begin();
            for (int i = 0; i < actionCount; i++) {
                IRI actionIri = vf.createIRI("http://test/action" + i);
                IRI resourceIri = vf.createIRI("http://test/resource" + i);

                conn.add(actionIri, RDF.TYPE, actionType);
                conn.add(actionIri, hasCurrentState, stateValidated);
                conn.add(actionIri, targetsEntity, resourceIri);
                conn.add(actionIri, hasActionID, vf.createLiteral("action" + i));
                conn.add(resourceIri, registry.resourcesOntology("resourceName"), vf.createLiteral("res" + i));

                conn.add(actionIri, protocol, vf.createLiteral("REST"));
                conn.add(actionIri, instruction, vf.createLiteral("http://instruction/" + i));
                conn.add(actionIri, statusCode, vf.createLiteral("200", org.eclipse.rdf4j.model.vocabulary.XSD.INTEGER));
            }
            conn.commit();
        }

        List<ActiveActionSummary> active = gateway.findActiveActions();
        assertTrue(active.size() >= actionCount);

        // Reset sparqlRepository spy to clear findActiveActions() call
        reset(sparqlRepository);

        long start = System.currentTimeMillis();
        mapePipeline.run();
        long end = System.currentTimeMillis();

        System.out.println("Processed " + actionCount + " actions in " + (end - start) + "ms");

        // Manually trigger outbox processing to dispatch actions to mock rabbitTemplate
        outboxService.processOutbox();

        // Verification:
        // 1. Check if messages were sent (sampling)
        verify(rabbitTemplate, times(actionCount)).convertAndSend(anyString(), anyString(), any(Object.class));

        // 2. Check batching: fetchActionStructures should be called 4 times (200 / 50)
        // plus 1 call for findActiveActions() at the start of pipeline.run()
        verify(sparqlRepository, times(1)).findActiveActions();
        verify(sparqlRepository, times(4)).fetchActionStructures(anyString());
    }
}
