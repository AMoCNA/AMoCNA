package com.kubiki.palamedes.integration;

import com.kubiki.common.model.ActionMessage;
import com.kubiki.common.model.ActionStatusUpdate;
import com.kubiki.common.model.ExecutionStatus;
import com.kubiki.palamedes.analyzer.AnomalyAgent;
import com.kubiki.palamedes.knowledge.GraphDBGateway;
import com.kubiki.common.ontology.OntologyRegistry;
import com.kubiki.palamedes.model.ActiveActionSummary;
import com.kubiki.palamedes.pipeline.MapePipeline;
import com.kubiki.palamedes.saga.SagaManager;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.RDFS;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class PalamedesMapeLoopIT {

    private static Repository inMemoryRepo;
    private final ValueFactory vf = SimpleValueFactory.getInstance();
    @Autowired
    private MapePipeline mapePipeline;
    @Autowired
    private SagaManager sagaManager;
    @Autowired
    private AnomalyAgent anomalyAgent;
    @Autowired
    private GraphDBGateway gateway;
    @Autowired
    private OntologyRegistry registry;
    @Autowired
    private com.kubiki.palamedes.dispatcher.OutboxService outboxService;
    @MockitoBean
    private RabbitTemplate rabbitTemplate;
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
    void shouldExecuteFullMapeLoop() {
        clearGraph();
        IRI pod = vf.createIRI("http://test/pod1");
        IRI state = vf.createIRI("http://test/pod1/state");
        IRI anomalyType = vf.createIRI("http://test/Anomaly1");
        IRI intent = vf.createIRI("http://test/RestartAction");

        try (RepositoryConnection conn = inMemoryRepo.getConnection()) {
            conn.add(pod, registry.resourcesOntology("hasState"), state);
            conn.add(state, RDF.TYPE, anomalyType);

            conn.add(anomalyType, RDFS.SUBCLASSOF, registry.bridgeOntology("Anomaly"));

            conn.add(pod, registry.resourcesOntology("resourceName"), vf.createLiteral("pod1"));

            IRI restriction = vf.createIRI("http://test/rest1");
            conn.add(anomalyType, RDFS.SUBCLASSOF, restriction);
            conn.add(restriction, RDF.TYPE, org.eclipse.rdf4j.model.vocabulary.OWL.RESTRICTION);
            conn.add(restriction, org.eclipse.rdf4j.model.vocabulary.OWL.ONPROPERTY, registry.bridgeOntology("isResolvedByIntent"));
            conn.add(restriction, org.eclipse.rdf4j.model.vocabulary.OWL.SOMEVALUESFROM, intent);

            conn.add(intent, RDF.TYPE, registry.actionsOntology("SimpleAction"));
            conn.add(intent, registry.actionsOntology("hasExecutionProtocol"), vf.createLiteral("REST"));
            conn.add(intent, registry.actionsOntology("hasExecutionInstruction"), vf.createLiteral("http://restart/{resourceName}"));
            conn.add(intent, registry.actionsOntology("hasExpectedStatusCode"), vf.createLiteral("200", org.eclipse.rdf4j.model.vocabulary.XSD.INTEGER));
        }

        anomalyAgent.analyze();

        List<ActiveActionSummary> active = gateway.findActiveActions();
        assertEquals(1, active.size());
        assertEquals("State_InProgress", active.get(0).stateFragment());
        IRI actionIri = active.get(0).actionIri();

        outboxService.processOutbox();

        verify(rabbitTemplate, atLeastOnce()).convertAndSend(anyString(), anyString(), any(ActionMessage.class));

        sagaManager.handleFeedback(new ActionStatusUpdate(actionIri.getLocalName(), ExecutionStatus.COMPLETED, null, 200));

        assertTrue(gateway.findActiveActions().isEmpty());
    }

    @Test
    void shouldDecomposeComplexWorkflow() {
        clearGraph();
        IRI pod = vf.createIRI("http://test/pod3");
        IRI state = vf.createIRI("http://test/pod3/state");
        IRI anomalyType = vf.createIRI("http://test/ScaleAnomaly");
        IRI intent = vf.createIRI("http://test/ScalingComplexWorkflow");

        IRI step1 = vf.createIRI("http://test/Step1");
        IRI step2 = vf.createIRI("http://test/Step2");

        try (RepositoryConnection conn = inMemoryRepo.getConnection()) {
            conn.add(pod, registry.resourcesOntology("hasState"), state);
            conn.add(state, RDF.TYPE, anomalyType);
            conn.add(anomalyType, RDFS.SUBCLASSOF, registry.bridgeOntology("Anomaly"));

            conn.add(pod, registry.resourcesOntology("resourceName"), vf.createLiteral("pod3"));

            IRI restriction = vf.createIRI("http://test/rest3");
            conn.add(anomalyType, RDFS.SUBCLASSOF, restriction);
            conn.add(restriction, RDF.TYPE, org.eclipse.rdf4j.model.vocabulary.OWL.RESTRICTION);
            conn.add(restriction, org.eclipse.rdf4j.model.vocabulary.OWL.ONPROPERTY, registry.bridgeOntology("isResolvedByIntent"));
            conn.add(restriction, org.eclipse.rdf4j.model.vocabulary.OWL.SOMEVALUESFROM, intent);

            conn.add(intent, RDF.TYPE, registry.actionsOntology("ComplexWorkflow"));
            conn.add(intent, registry.actionsOntology("isDecomposedInto"), step1);
            conn.add(intent, registry.actionsOntology("isDecomposedInto"), step2);

            // Step 1 details
            conn.add(step1, RDF.TYPE, registry.actionsOntology("SimpleAction"));
            conn.add(step1, registry.actionsOntology("hasExecutionProtocol"), vf.createLiteral("REST"));
            conn.add(step1, registry.actionsOntology("hasExecutionInstruction"), vf.createLiteral("http://step1"));
            conn.add(step1, registry.actionsOntology("hasExpectedStatusCode"), vf.createLiteral("200", org.eclipse.rdf4j.model.vocabulary.XSD.INTEGER));

            // Step 2 details
            conn.add(step2, RDF.TYPE, registry.actionsOntology("SimpleAction"));
            conn.add(step2, registry.actionsOntology("hasExecutionProtocol"), vf.createLiteral("REST"));
            conn.add(step2, registry.actionsOntology("hasExecutionInstruction"), vf.createLiteral("http://step2"));
            conn.add(step2, registry.actionsOntology("hasExpectedStatusCode"), vf.createLiteral("200", org.eclipse.rdf4j.model.vocabulary.XSD.INTEGER));
        }

        anomalyAgent.analyze();

        List<ActiveActionSummary> active = gateway.findActiveActions();
        assertTrue(active.size() >= 2);

        mapePipeline.run();

        active = gateway.findActiveActions();
        ActiveActionSummary child1 = active.stream()
                .filter(a -> a.stateFragment().equals("State_InProgress"))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Child 1 not found in IN_PROGRESS state"));

        outboxService.processOutbox();

        verify(rabbitTemplate, atLeastOnce()).convertAndSend(anyString(), anyString(), any(ActionMessage.class));

        sagaManager.handleFeedback(new ActionStatusUpdate(child1.actionIri().getLocalName(), ExecutionStatus.COMPLETED, null, 200));

        active = gateway.findActiveActions();
        assertTrue(active.stream().anyMatch(a -> a.stateFragment().equals("State_InProgress")), "Next step should have advanced to IN_PROGRESS");
    }
}
