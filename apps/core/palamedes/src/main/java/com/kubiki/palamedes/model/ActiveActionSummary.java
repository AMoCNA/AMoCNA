package com.kubiki.palamedes.model;

import org.eclipse.rdf4j.model.IRI;

public record ActiveActionSummary(
        IRI actionIri,
        IRI resourceIri,
        String resourceName,
        String stateFragment
) {
}

