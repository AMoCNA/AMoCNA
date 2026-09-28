package com.kubiki.metis.pbt;

// Feature: metis-monitor-module, Property 3: Functional state invariant

import com.kubiki.daedalus.context.GlobalTemplateContext;
import com.kubiki.daedalus.core.Formatter;
import com.kubiki.daedalus.core.format.IriFormatter;
import com.kubiki.daedalus.core.format.LiteralFormatter;
import com.kubiki.daedalus.core.format.PlainFormatter;
import com.kubiki.daedalus.proxy.DaedalusInvocationHandler;
import com.kubiki.metis.config.MetisProperties;
import com.kubiki.metis.grpc.StateChangedEvent;
import com.kubiki.metis.knowledge.KnowledgeBaseException;
import com.kubiki.metis.knowledge.KnowledgeBaseWriter;
import com.kubiki.metis.knowledge.MetisDaedalusRepository;
import com.kubiki.metis.sensor.IriFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.sail.memory.MemoryStore;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 3: Functional state invariant.
 * <p>
 * Validates: Requirements 1.2, 1.3
 */
class StateChangedPropertyTest {

    private static final String CNEE = "http://www.semanticweb.org/szymo/ontologies/2026/2/CNEEOnt/";

    private KnowledgeBaseWriter writerFor(Repository repo) {
        MetisProperties props = new MetisProperties(
                new MetisProperties.GraphDB("http://localhost:7200", "test", 5000),
                new MetisProperties.Ontology(CNEE),
                null
        );
        IriFactory registry = new IriFactory(props);

        GlobalTemplateContext ctx = new GlobalTemplateContext();
        ctx.set("SPARQL_PREFIXES", "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n" +
                "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\n" +
                "PREFIX cnee: <" + CNEE + ">");

        Formatter formatter = new Formatter(List.of(new PlainFormatter(), new IriFormatter(), new LiteralFormatter()));
        DaedalusInvocationHandler handler = new DaedalusInvocationHandler(MetisDaedalusRepository.class, ctx, formatter, repo);
        MetisDaedalusRepository repository = (MetisDaedalusRepository) Proxy.newProxyInstance(
                MetisDaedalusRepository.class.getClassLoader(),
                new Class[]{MetisDaedalusRepository.class},
                handler
        );
        var meterRegistry = new SimpleMeterRegistry();
        return new KnowledgeBaseWriter(repository, registry, meterRegistry);
    }

    @Property(tries = 50)
    void functionalStateInvariant(
            @ForAll("resourceIris") String resourceIri,
            @ForAll("cneeIris") String stateA,
            @ForAll("cneeIris") String stateB
    ) throws KnowledgeBaseException {
        Repository repo = new SailRepository(new MemoryStore());
        repo.init();
        KnowledgeBaseWriter writer = writerFor(repo);

        ValueFactory vf = SimpleValueFactory.getInstance();
        IRI subject = vf.createIRI(resourceIri);
        IRI hasState = vf.createIRI(CNEE + "hasState");

        // Act: Apply two state changes
        String sparqlA = writer.changeState(StateChangedEvent.newBuilder().setResourceIri(resourceIri).setNewStateIri(stateA).build());
        String sparqlB = writer.changeState(StateChangedEvent.newBuilder().setResourceIri(resourceIri).setNewStateIri(stateB).build());

        try (RepositoryConnection conn = repo.getConnection()) {
            conn.prepareUpdate(sparqlA).execute();
            conn.prepareUpdate(sparqlB).execute();
        }

        try (RepositoryConnection conn = repo.getConnection()) {
            // Verify only one state exists (Requirement 1.2, 1.3)
            long stateCount = conn.getStatements(subject, hasState, null, false).stream().count();
            assertThat(stateCount).isEqualTo(1);
            assertThat(conn.hasStatement(subject, hasState, vf.createIRI(stateB), false)).isTrue();
        }
        repo.shutDown();
    }

    @net.jqwik.api.Provide
    net.jqwik.api.Arbitrary<String> resourceIris() {
        return net.jqwik.api.Arbitraries.strings().withCharRange('a', 'z').ofMinLength(1).map(s -> "http://ex.org/" + s);
    }

    @net.jqwik.api.Provide
    net.jqwik.api.Arbitrary<String> cneeIris() {
        return net.jqwik.api.Arbitraries.strings().withCharRange('a', 'z').ofMinLength(1).map(s -> CNEE + s);
    }
}
