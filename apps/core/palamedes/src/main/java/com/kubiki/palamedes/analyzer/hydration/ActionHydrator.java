package com.kubiki.palamedes.analyzer.hydration;

import com.kubiki.palamedes.model.AnomalyTarget;
import org.eclipse.rdf4j.model.IRI;

import java.util.Map;

/**
 * Strategy for hydrating action parameters based on the remediation intent.
 */
public interface ActionHydrator {
    /**
     * Checks if this hydrator supports the given intent.
     */
    boolean supports(IRI intentIri);

    /**
     * Performs hydration of parameters for the given target and intent.
     *
     * @return Map of parameter names to their hydrated values.
     */
    Map<String, String> hydrate(AnomalyTarget target);
}
