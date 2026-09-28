package com.kubiki.palamedes.model;

import org.eclipse.rdf4j.model.IRI;

public record IntentMetadata(
        IRI intentId,
        float riskMultiplier,
        int cardinalityCap,
        boolean isHealing
) {
}
