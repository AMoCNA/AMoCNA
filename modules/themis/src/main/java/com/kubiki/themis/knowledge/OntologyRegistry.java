package com.kubiki.themis.knowledge;

import com.kubiki.themis.config.ThemisProperties;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.springframework.stereotype.Component;

@Component
public class OntologyRegistry {

    private final ThemisProperties themisProperties;
    private final ValueFactory valueFactory = SimpleValueFactory.getInstance();

    public OntologyRegistry(ThemisProperties themisProperties) {
        this.themisProperties = themisProperties;
    }

    public IRI moam(String fragment) {
        return valueFactory.createIRI(themisProperties.ontology().moamNamespace(), fragment);
    }

    public String getMoamNamespace() {
        return themisProperties.ontology().moamNamespace();
    }
}
