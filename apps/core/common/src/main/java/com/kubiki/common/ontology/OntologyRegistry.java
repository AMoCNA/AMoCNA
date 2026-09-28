package com.kubiki.common.ontology;

import com.kubiki.common.config.AmocnaCommonProperties;
import lombok.RequiredArgsConstructor;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OntologyRegistry {

    public static final String HTTP = "http";
    private final AmocnaCommonProperties properties;
    private final ValueFactory valueFactory = SimpleValueFactory.getInstance();


    public IRI actionsOntology(String fragment) {
        if (fragment != null && fragment.startsWith(HTTP)) {
            return valueFactory.createIRI(fragment);
        }
        return valueFactory.createIRI(properties.ontology().actionsNamespace(), fragment);
    }

    public IRI resourcesOntology(String fragment) {
        if (fragment != null && fragment.startsWith(HTTP)) {
            return valueFactory.createIRI(fragment);
        }
        return valueFactory.createIRI(properties.ontology().resourcesNamespace(), fragment);
    }

    public IRI bridgeOntology(String fragment) {
        if (fragment != null && fragment.startsWith(HTTP)) {
            return valueFactory.createIRI(fragment);
        }
        return valueFactory.createIRI(properties.ontology().bridgeNamespace(), fragment);
    }
}
