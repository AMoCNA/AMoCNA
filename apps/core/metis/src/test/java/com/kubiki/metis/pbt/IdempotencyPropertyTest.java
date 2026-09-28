package com.kubiki.metis.pbt;

// Feature: metis-monitor-module, Property 7: Idempotency across all event types

import com.kubiki.daedalus.context.GlobalTemplateContext;
import com.kubiki.daedalus.core.Formatter;
import com.kubiki.daedalus.core.format.IriFormatter;
import com.kubiki.daedalus.core.format.LiteralFormatter;
import com.kubiki.daedalus.core.format.PlainFormatter;
import com.kubiki.daedalus.proxy.DaedalusInvocationHandler;
import com.kubiki.metis.config.MetisProperties;
import com.kubiki.metis.grpc.EntityDiscoveredEvent;
import com.kubiki.metis.grpc.RelationshipAssertedEvent;
import com.kubiki.metis.knowledge.KnowledgeBaseException;
import com.kubiki.metis.knowledge.KnowledgeBaseWriter;
import com.kubiki.metis.knowledge.MetisDaedalusRepository;
import com.kubiki.metis.sensor.IriFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.sail.memory.MemoryStore;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 7: Idempotency across all event types.
 * <p>
 * Validates: Requirements 12.1, 12.2
 */
class IdempotencyPropertyTest {

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

    /**
     * Returns the total number of triples currently in the repository.
     */
    private long tripleCount(Repository repo) {
        try (RepositoryConnection conn = repo.getConnection()) {
            return conn.size();
        }
    }

    @Property(tries = 50)
    void insertEntityIdempotency(@ForAll String iri, @ForAll String type) throws KnowledgeBaseException {
        if (iri.isBlank() || type.isBlank() || !type.startsWith(CNEE)) return;
        Repository repo = new SailRepository(new MemoryStore());
        repo.init();
        KnowledgeBaseWriter writer = writerFor(repo);

        EntityDiscoveredEvent event = EntityDiscoveredEvent.newBuilder()
                .setResourceIri(iri).setOntologyType(type)
                .setResourceId("id").setResourceName("name").build();

        writer.insertEntity(event);
        long firstCount = tripleCount(repo);
        assertThat(firstCount).isGreaterThan(0);

        writer.insertEntity(event);
        assertThat(tripleCount(repo)).isEqualTo(firstCount);
        repo.shutDown();
    }

    @Property(tries = 50)
    void assertRelationshipIdempotency(@ForAll String s, @ForAll String o) throws KnowledgeBaseException {
        if (s.isBlank() || o.isBlank() || !s.startsWith("http") || !o.startsWith("http")) return;
        Repository repo = new SailRepository(new MemoryStore());
        repo.init();
        KnowledgeBaseWriter writer = writerFor(repo);

        RelationshipAssertedEvent event = RelationshipAssertedEvent.newBuilder()
                .setSubjectIri(s).setPredicate(CNEE + "communicatesWith").setObjectIri(o).build();

        writer.assertRelationship(event);
        long firstCount = tripleCount(repo);
        assertThat(firstCount).isGreaterThan(0);

        writer.assertRelationship(event);
        assertThat(tripleCount(repo)).isEqualTo(firstCount);
        repo.shutDown();
    }
}
