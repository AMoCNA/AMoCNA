package com.kubiki.metis.pbt;

import com.kubiki.daedalus.context.GlobalTemplateContext;
import com.kubiki.daedalus.core.Formatter;
import com.kubiki.daedalus.core.format.IriFormatter;
import com.kubiki.daedalus.core.format.LiteralFormatter;
import com.kubiki.daedalus.core.format.PlainFormatter;
import com.kubiki.daedalus.proxy.DaedalusInvocationHandler;
import com.kubiki.metis.config.MetisProperties;
import com.kubiki.metis.grpc.EntityDiscoveredEvent;
import com.kubiki.metis.knowledge.KnowledgeBaseException;
import com.kubiki.metis.knowledge.KnowledgeBaseWriter;
import com.kubiki.metis.knowledge.MetisDaedalusRepository;
import com.kubiki.metis.sensor.IriFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.jqwik.api.*;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.sail.memory.MemoryStore;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * // Feature: metis-monitor-module, Property 2: Mandatory triples and ontology type conformance
 * <p>
 * Validates: Requirements 4.1, 4.2, 4.3, 13.1, 13.2
 */
class EntityDiscoveredPropertyTest {

    private static final String CNEE_NAMESPACE =
            "http://www.semanticweb.org/szymo/ontologies/2026/2/CNEEOnt/";

    // -------------------------------------------------------------------------
    // Arbitraries
    // -------------------------------------------------------------------------

    /**
     * Generates a non-empty alphanumeric string of length 1–20.
     */
    @Provide
    Arbitrary<String> nonEmptyStrings() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(1)
                .ofMaxLength(20);
    }

    /**
     * Generates a valid CNEEOnt-namespaced ontology type IRI.
     */
    @Provide
    Arbitrary<String> cneeOntologyTypes() {
        return nonEmptyStrings().map(fragment -> CNEE_NAMESPACE + fragment);
    }

    /**
     * Generates a valid absolute resource IRI.
     */
    @Provide
    Arbitrary<String> resourceIris() {
        return nonEmptyStrings().map(s -> "http://example.org/resource/" + s);
    }

    // -------------------------------------------------------------------------
    // Property test
    // -------------------------------------------------------------------------

    /**
     * Property 2: Mandatory triples and ontology type conformance.
     * <p>
     * For any valid EntityDiscoveredEvent with a CNEEOnt-namespaced ontology_type,
     * non-empty resource_iri, resource_id, and resource_name, the SPARQL update
     * produced by KnowledgeBaseWriter must contain:
     * - exactly one rdf:type triple whose object IRI begins with the CNEEOnt namespace,
     * - exactly one cnee:resourceID data property triple,
     * - exactly one cnee:resourceName data property triple.
     * <p>
     * // Feature: metis-monitor-module, Property 2: Mandatory triples and ontology type conformance
     * <p>
     * Validates: Requirements 4.1, 4.2, 4.3, 13.1, 13.2
     */
    @Property(tries = 100)
    void mandatoryTriplesAndOntologyTypeConformance(
            @ForAll("resourceIris") String resourceIri,
            @ForAll("cneeOntologyTypes") String ontologyType,
            @ForAll("nonEmptyStrings") String resourceId,
            @ForAll("nonEmptyStrings") String resourceName
    ) throws KnowledgeBaseException {
        // Arrange: set up a real in-memory repository
        Repository repo = new SailRepository(new MemoryStore());
        repo.init();

        MetisProperties props = new MetisProperties(
                new MetisProperties.GraphDB("http://localhost:7200", "test", 5000),
                new MetisProperties.Ontology(CNEE_NAMESPACE),
                null
        );
        IriFactory iriFactory = new IriFactory(props);

        GlobalTemplateContext ctx = new GlobalTemplateContext();
        ctx.set("SPARQL_PREFIXES", "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n" +
                "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\n" +
                "PREFIX cnee: <" + CNEE_NAMESPACE + ">");

        Formatter formatter = new Formatter(List.of(new PlainFormatter(), new IriFormatter(), new LiteralFormatter()));
        DaedalusInvocationHandler handler = new DaedalusInvocationHandler(MetisDaedalusRepository.class, ctx, formatter, repo);
        MetisDaedalusRepository repository = (MetisDaedalusRepository) Proxy.newProxyInstance(
                MetisDaedalusRepository.class.getClassLoader(),
                new Class[]{MetisDaedalusRepository.class},
                handler
        );

        var meterRegistry = new SimpleMeterRegistry();

        KnowledgeBaseWriter writer = new KnowledgeBaseWriter(repository, iriFactory, meterRegistry);

        // Build the event
        EntityDiscoveredEvent event = EntityDiscoveredEvent.newBuilder()
                .setResourceIri(resourceIri)
                .setOntologyType(ontologyType)
                .setResourceId(resourceId)
                .setResourceName(resourceName)
                .build();

        // Act
        String sparql = writer.insertEntity(event);
        try (RepositoryConnection conn = repo.getConnection()) {
            conn.prepareUpdate(sparql).execute();
        }

        // Assert: verify triples directly in the repository
        ValueFactory vf = SimpleValueFactory.getInstance();
        IRI subject = vf.createIRI(resourceIri);
        IRI rdfType = RDF.TYPE;
        IRI cneeResourceId = vf.createIRI(CNEE_NAMESPACE + "resourceID");
        IRI cneeResourceName = vf.createIRI(CNEE_NAMESPACE + "resourceName");

        try (RepositoryConnection conn = repo.getConnection()) {
            // exactly one rdf:type triple whose object IRI begins with the CNEEOnt namespace
            long rdfTypeCount = conn.getStatements(subject, rdfType, null, false).stream()
                    .filter(s -> s.getObject().stringValue().startsWith(CNEE_NAMESPACE))
                    .count();
            assertThat(rdfTypeCount)
                    .as("Expected exactly one rdf:type with CNEEOnt-namespaced object")
                    .isEqualTo(1);

            // exactly one cnee:resourceID data property triple
            long resourceIdCount = conn.getStatements(subject, cneeResourceId, null, false).stream().count();
            assertThat(resourceIdCount)
                    .as("Expected exactly one cnee:resourceID triple")
                    .isEqualTo(1);

            // exactly one cnee:resourceName data property triple
            long resourceNameCount = conn.getStatements(subject, cneeResourceName, null, false).stream().count();
            assertThat(resourceNameCount)
                    .as("Expected exactly one cnee:resourceName triple")
                    .isEqualTo(1);
        }

        repo.shutDown();
    }
}
