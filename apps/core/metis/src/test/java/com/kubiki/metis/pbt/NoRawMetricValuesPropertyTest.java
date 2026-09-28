package com.kubiki.metis.pbt;

// Feature: metis-monitor-module, Property 1: No raw metric values in GraphDB

import com.kubiki.daedalus.context.GlobalTemplateContext;
import com.kubiki.daedalus.core.Formatter;
import com.kubiki.daedalus.core.format.IriFormatter;
import com.kubiki.daedalus.core.format.LiteralFormatter;
import com.kubiki.daedalus.core.format.PlainFormatter;
import com.kubiki.daedalus.proxy.DaedalusInvocationHandler;
import com.kubiki.metis.config.MetisProperties;
import com.kubiki.metis.grpc.MetricMetadataRegisteredEvent;
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
 * Property 1: No raw metric values in GraphDB.
 * <p>
 * Validates: Requirement 2.1
 */
class NoRawMetricValuesPropertyTest {

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
    void registerMetricMetadata_noMetricValues(@ForAll String iri, @ForAll String url, @ForAll String name) throws KnowledgeBaseException {
        if (iri.isBlank() || url.isBlank() || name.isBlank() || !url.startsWith("http")) return;
        Repository repo = new SailRepository(new MemoryStore());
        repo.init();
        KnowledgeBaseWriter writer = writerFor(repo);

        MetricMetadataRegisteredEvent event = MetricMetadataRegisteredEvent.newBuilder()
                .setResourceIri(iri).setEndpointUrl(url).setMetricName(name).build();

        writer.registerMetricMetadata(event);

        try (RepositoryConnection conn = repo.getConnection()) {
            // Verify no triples with literal metric values (Requirement 2.1)
            long valueTriples = conn.getStatements(null, null, null, false).stream()
                    .filter(s -> s.getObject().toString().matches("^-?\\d+(\\.\\d+)?$"))
                    .count();
            assertThat(valueTriples).isEqualTo(0);
        }
        repo.shutDown();
    }
}
