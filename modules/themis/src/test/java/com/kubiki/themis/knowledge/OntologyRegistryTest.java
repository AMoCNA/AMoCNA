package com.kubiki.themis.knowledge;

import com.kubiki.themis.config.ThemisProperties;
import org.eclipse.rdf4j.model.IRI;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class OntologyRegistryTest {

    @Test
    void shouldCreateMoamIRI() {
        // Arrange
        ThemisProperties.Ontology ontology = new ThemisProperties.Ontology("http://example.org/moam#");
        ThemisProperties properties = new ThemisProperties(null, ontology, null);
        OntologyRegistry registry = new OntologyRegistry(properties);

        // Act
        IRI iri = registry.moam("Action");

        // Assert
        assertEquals("http://example.org/moam#Action", iri.stringValue());
    }
}
