package com.kubiki.themis.knowledge;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SparqlLoaderTest {

    @Mock
    private ResourceLoader resourceLoader;
    @Mock
    private Resource resource;

    private SparqlLoader sparqlLoader;

    @BeforeEach
    void setUp() {
        sparqlLoader = new SparqlLoader(resourceLoader);
    }

    @Test
    void shouldLoadRawSparqlTemplate() throws IOException {
        String templateContent = "SELECT * WHERE { ${resourceIri} ?p ?o }";
        when(resourceLoader.getResource("classpath:sparql/test-template.sparql")).thenReturn(resource);
        when(resource.getContentAsString(StandardCharsets.UTF_8)).thenReturn(templateContent);

        String query = sparqlLoader.loadRaw("test-template");
        
        assertThat(query).isEqualTo(templateContent);
    }
}
