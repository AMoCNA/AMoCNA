package com.kubiki.palamedes.analyzer.hydration;

import com.kubiki.palamedes.model.AnomalyTarget;
import org.eclipse.rdf4j.model.IRI;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Fallback hydrator for simple actions with only basic parameters.
 */
@Order(Integer.MAX_VALUE)
@Component
public class DefaultActionHydrator extends BaseActionHydrator {
    @Override
    public boolean supports(IRI intentIri) {
        return true; // Catch-all
    }

    @Override
    public Map<String, String> hydrate(AnomalyTarget target) {
        return getBaseParameters(target);
    }
}
