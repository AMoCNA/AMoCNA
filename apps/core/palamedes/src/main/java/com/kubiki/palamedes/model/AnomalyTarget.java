package com.kubiki.palamedes.model;

import org.eclipse.rdf4j.model.IRI;

public record AnomalyTarget(
        IRI resourceIri,
        String resourceName,
        IRI intentIri
) {
}

