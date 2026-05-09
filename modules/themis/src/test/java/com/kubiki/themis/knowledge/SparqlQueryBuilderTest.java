package com.kubiki.themis.knowledge;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SparqlQueryBuilderTest {

    @Mock
    private SparqlLoader sparqlLoader;
    @Mock
    private OntologyRegistry ontologyRegistry;

    private SparqlQueryBuilder queryBuilder;
    private final SimpleValueFactory vf = SimpleValueFactory.getInstance();

    @BeforeEach
    void setUp() {
        queryBuilder = new SparqlQueryBuilder(sparqlLoader, ontologyRegistry);
    }

    @Test
    void shouldBuildQueryWithPrefixesAndVariables() {
        when(sparqlLoader.loadRaw("test-query")).thenReturn("SELECT * WHERE { ${subject} ?p ${object} }");
        when(ontologyRegistry.getMoamNamespace()).thenReturn("http://example.org/moam#");
        
        IRI subject = vf.createIRI("http://example.org/resource1");
        String object = "test-value";
        
        String query = queryBuilder.builder()
                .template("test-query")
                .variable("subject", subject)
                .variable("object", object)
                .build();
        
        assertThat(query).startsWith("PREFIX moam: <http://example.org/moam#>");
        assertThat(query).contains("<http://example.org/resource1>");
        assertThat(query).contains("\"test-value\"");
    }
}
