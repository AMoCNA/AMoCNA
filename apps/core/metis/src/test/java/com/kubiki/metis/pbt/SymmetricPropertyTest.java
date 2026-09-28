package com.kubiki.metis.pbt;

// Feature: metis-monitor-module, Property 5: Symmetric communicatesWith

import com.kubiki.daedalus.context.GlobalTemplateContext;
import com.kubiki.daedalus.core.Formatter;
import com.kubiki.daedalus.core.format.IriFormatter;
import com.kubiki.daedalus.core.format.LiteralFormatter;
import com.kubiki.daedalus.core.format.PlainFormatter;
import com.kubiki.daedalus.proxy.DaedalusInvocationHandler;
import com.kubiki.metis.config.MetisProperties;
import com.kubiki.metis.grpc.RelationshipAssertedEvent;
import com.kubiki.metis.knowledge.KnowledgeBaseException;
import com.kubiki.metis.knowledge.KnowledgeBaseWriter;
import com.kubiki.metis.knowledge.MetisDaedalusRepository;
import com.kubiki.metis.sensor.IriFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
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
 * Property 5: Symmetric communicatesWith.
 * <p>
 * Validates: Requirements 6.1, 6.2
 */
class SymmetricPropertyTest {

    private static final String CNEE = "http://www.semanticweb.org/szymo/ontologies/2026/2/CNEEOnt/";

    @Property(tries = 50)
    void symmetricCommunicatesWith(@ForAll String iriA, @ForAll String iriB) throws KnowledgeBaseException {
        if (iriA.isBlank() || iriB.isBlank() || !iriA.startsWith("http") || !iriB.startsWith("http")) return;
        Repository repo = new SailRepository(new MemoryStore());
        repo.init();

        MetisProperties props = new MetisProperties(
                new MetisProperties.GraphDB("http://localhost:7200", "test", 5000),
                new MetisProperties.Ontology(CNEE),
                null
        );
        IriFactory iriFactory = new IriFactory(props);

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

        KnowledgeBaseWriter writer = new KnowledgeBaseWriter(repository, iriFactory, meterRegistry);

        RelationshipAssertedEvent event = RelationshipAssertedEvent.newBuilder()
                .setSubjectIri(iriA)
                .setPredicate(CNEE + "communicatesWith")
                .setObjectIri(iriB)
                .build();

        writer.assertRelationship(event);

        ValueFactory vf = SimpleValueFactory.getInstance();
        try (RepositoryConnection conn = repo.getConnection()) {
            assertThat(conn.hasStatement(vf.createIRI(iriA), vf.createIRI(CNEE + "communicatesWith"), vf.createIRI(iriB), false)).isTrue();
            assertThat(conn.hasStatement(vf.createIRI(iriB), vf.createIRI(CNEE + "communicatesWith"), vf.createIRI(iriA), false)).isTrue();
        }
        repo.shutDown();
    }
}
