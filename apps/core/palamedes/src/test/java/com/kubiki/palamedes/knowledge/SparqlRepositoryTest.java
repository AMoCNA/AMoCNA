package com.kubiki.palamedes.knowledge;

import com.kubiki.common.config.AmocnaCommonProperties;
import com.kubiki.daedalus.context.GlobalTemplateContext;
import com.kubiki.palamedes.config.BeanConfig;
import com.kubiki.palamedes.config.DaedalusInitializer;
import com.kubiki.palamedes.config.GraphDBConfig;
import com.kubiki.palamedes.config.PalamedesProperties;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.sail.memory.MemoryStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = {BeanConfig.class, DaedalusInitializer.class, GraphDBConfig.class})
@EnableConfigurationProperties({PalamedesProperties.class, AmocnaCommonProperties.class})
@ActiveProfiles("test")
class SparqlRepositoryTest {

    private static Repository inMemoryRepo;
    private final ValueFactory vf = SimpleValueFactory.getInstance();

    @MockitoBean
    private Repository realRepository;

    @Autowired
    private SparqlRepository sparqlRepository;

    @Autowired
    private GlobalTemplateContext globalContext;

    @BeforeEach
    void setUp() throws Exception {
        if (inMemoryRepo == null) {
            inMemoryRepo = new SailRepository(new MemoryStore());
            inMemoryRepo.init();
        }
        try (RepositoryConnection conn = inMemoryRepo.getConnection()) {
            conn.begin();
            conn.clear();
            conn.commit();
        }
        when(realRepository.getConnection()).thenAnswer(inv -> inMemoryRepo.getConnection());
        when(realRepository.getValueFactory()).thenReturn(vf);
    }

    @Test
    void shouldInjectAndHydrateFindAnomalies() {
        assertThat(sparqlRepository).isNotNull();

        List<BindingSet> result = sparqlRepository.findAnomalies();

        assertThat(result).isNotNull();
    }

    @Test
    void shouldHydrateParameterizedQuery() {
        String actionId = "http://example.org/action/123";
        List<BindingSet> result = sparqlRepository.findDependents(actionId);

        assertThat(result).isNotNull();
    }

    @Test
    void shouldHydrateBulkActionStructureQuery() {
        String action1 = "http://example.org/action/1";
        String action2 = "http://example.org/action/2";
        String actions = "<" + action1 + "> <" + action2 + ">";

        List<BindingSet> result = sparqlRepository.fetchActionStructures(actions);

        assertThat(result).isNotNull();
    }

    @Test
    void shouldFetchActionHydrationsInheritedFromParent() throws Exception {
        org.eclipse.rdf4j.model.IRI parentAction = vf.createIRI("http://example.org/action/parent");
        org.eclipse.rdf4j.model.IRI childAction = vf.createIRI("http://example.org/action/child");

        org.eclipse.rdf4j.model.IRI isDecomposedInto = vf.createIRI("http://www.semanticweb.org/patryk/ontologies/2026/4/MoaMont#isDecomposedInto");
        org.eclipse.rdf4j.model.IRI hydrationPayload = vf.createIRI("http://www.semanticweb.org/patryk/ontologies/2026/4/MoaMont#hydrationPayload");

        try (RepositoryConnection conn = inMemoryRepo.getConnection()) {
            conn.begin();
            conn.add(parentAction, isDecomposedInto, childAction);
            conn.add(parentAction, hydrationPayload, vf.createLiteral("{\"namespace\":\"sock-shop\"}"));
            conn.commit();
        }

        List<BindingSet> result = sparqlRepository.fetchActionHydrations("<" + childAction.stringValue() + ">");

        assertThat(result).hasSize(1);
        BindingSet bs = result.get(0);
        assertThat(bs.getValue("action")).isEqualTo(childAction);
        assertThat(bs.getValue("payload").stringValue()).isEqualTo("{\"namespace\":\"sock-shop\"}");
    }

    @Test
    void shouldFetchActionHydrationsInheritedFromDeepAncestor() throws Exception {
        org.eclipse.rdf4j.model.IRI grandparentAction = vf.createIRI("http://example.org/action/grandparent");
        org.eclipse.rdf4j.model.IRI parentAction = vf.createIRI("http://example.org/action/parent");
        org.eclipse.rdf4j.model.IRI childAction = vf.createIRI("http://example.org/action/child");

        org.eclipse.rdf4j.model.IRI isDecomposedInto = vf.createIRI("http://www.semanticweb.org/patryk/ontologies/2026/4/MoaMont#isDecomposedInto");
        org.eclipse.rdf4j.model.IRI hydrationPayload = vf.createIRI("http://www.semanticweb.org/patryk/ontologies/2026/4/MoaMont#hydrationPayload");

        try (RepositoryConnection conn = inMemoryRepo.getConnection()) {
            conn.begin();
            conn.add(grandparentAction, isDecomposedInto, parentAction);
            conn.add(parentAction, isDecomposedInto, childAction);
            conn.add(grandparentAction, hydrationPayload, vf.createLiteral("{\"namespace\":\"sock-shop\",\"depth\":\"deep\"}"));
            conn.commit();
        }

        List<BindingSet> result = sparqlRepository.fetchActionHydrations("<" + childAction.stringValue() + ">");

        assertThat(result).hasSize(1);
        BindingSet bs = result.get(0);
        assertThat(bs.getValue("action")).isEqualTo(childAction);
        assertThat(bs.getValue("payload").stringValue()).isEqualTo("{\"namespace\":\"sock-shop\",\"depth\":\"deep\"}");
    }

    @Test
    void shouldFetchActionHydrationsInheritedFromCompensation() throws Exception {
        org.eclipse.rdf4j.model.IRI parentAction = vf.createIRI("http://example.org/action/parent");
        org.eclipse.rdf4j.model.IRI stepAction = vf.createIRI("http://example.org/action/step");
        org.eclipse.rdf4j.model.IRI compensationAction = vf.createIRI("http://example.org/action/compensation");

        org.eclipse.rdf4j.model.IRI isDecomposedInto = vf.createIRI("http://www.semanticweb.org/patryk/ontologies/2026/4/MoaMont#isDecomposedInto");
        org.eclipse.rdf4j.model.IRI hasCompensation = vf.createIRI("http://www.semanticweb.org/patryk/ontologies/2026/4/MoaMont#hasCompensation");
        org.eclipse.rdf4j.model.IRI hydrationPayload = vf.createIRI("http://www.semanticweb.org/patryk/ontologies/2026/4/MoaMont#hydrationPayload");

        try (RepositoryConnection conn = inMemoryRepo.getConnection()) {
            conn.begin();
            conn.add(parentAction, isDecomposedInto, stepAction);
            conn.add(stepAction, hasCompensation, compensationAction);
            conn.add(parentAction, hydrationPayload, vf.createLiteral("{\"namespace\":\"sock-shop\"}"));
            conn.commit();
        }

        List<BindingSet> result = sparqlRepository.fetchActionHydrations("<" + compensationAction.stringValue() + ">");

        assertThat(result).hasSize(1);
        BindingSet bs = result.get(0);
        assertThat(bs.getValue("action")).isEqualTo(compensationAction);
        assertThat(bs.getValue("payload").stringValue()).isEqualTo("{\"namespace\":\"sock-shop\"}");
    }

    @Test
    void shouldFetchActionHydrationsInheritedFromCompensationCaseB() throws Exception {
        org.eclipse.rdf4j.model.IRI parentAction = vf.createIRI("http://example.org/action/parent");
        org.eclipse.rdf4j.model.IRI stepAction = vf.createIRI("http://example.org/action/step");
        org.eclipse.rdf4j.model.IRI compensationAction = vf.createIRI("http://example.org/action/compensation");
        org.eclipse.rdf4j.model.IRI resource = vf.createIRI("http://example.org/resource");

        org.eclipse.rdf4j.model.IRI stepIntent = vf.createIRI("http://example.org/intent/step");
        org.eclipse.rdf4j.model.IRI compIntent = vf.createIRI("http://example.org/intent/compensation");

        org.eclipse.rdf4j.model.IRI isDecomposedInto = vf.createIRI("http://www.semanticweb.org/patryk/ontologies/2026/4/MoaMont#isDecomposedInto");
        org.eclipse.rdf4j.model.IRI hasCompensation = vf.createIRI("http://www.semanticweb.org/patryk/ontologies/2026/4/MoaMont#hasCompensation");
        org.eclipse.rdf4j.model.IRI targetsEntity = vf.createIRI("http://www.semanticweb.org/patryk/ontologies/2026/4/MoaMont#targetsEntity");
        org.eclipse.rdf4j.model.IRI hydrationPayload = vf.createIRI("http://www.semanticweb.org/patryk/ontologies/2026/4/MoaMont#hydrationPayload");

        try (RepositoryConnection conn = inMemoryRepo.getConnection()) {
            conn.begin();
            // Decomposition hierarchy
            conn.add(parentAction, isDecomposedInto, stepAction);
            
            // Types
            conn.add(stepAction, org.eclipse.rdf4j.model.vocabulary.RDF.TYPE, stepIntent);
            conn.add(compensationAction, org.eclipse.rdf4j.model.vocabulary.RDF.TYPE, compIntent);
            
            // Ontology connection
            conn.add(stepIntent, hasCompensation, compIntent);
            
            // Targets resource
            conn.add(stepAction, targetsEntity, resource);
            conn.add(compensationAction, targetsEntity, resource);
            
            // Hydration on parent
            conn.add(parentAction, hydrationPayload, vf.createLiteral("{\"namespace\":\"sock-shop\"}"));
            conn.commit();
        }

        List<BindingSet> result = sparqlRepository.fetchActionHydrations("<" + compensationAction.stringValue() + ">");

        assertThat(result).hasSize(1);
        BindingSet bs = result.get(0);
        assertThat(bs.getValue("action")).isEqualTo(compensationAction);
        assertThat(bs.getValue("payload").stringValue()).isEqualTo("{\"namespace\":\"sock-shop\"}");
    }
}
