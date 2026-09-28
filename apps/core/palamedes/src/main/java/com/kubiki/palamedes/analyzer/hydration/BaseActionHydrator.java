package com.kubiki.palamedes.analyzer.hydration;

import com.kubiki.palamedes.analyzer.ImageRemediationPlanner;
import com.kubiki.palamedes.model.AnomalyTarget;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Base hydrator providing common parameters like namespace and resourceName.
 */
@Component
public abstract class BaseActionHydrator implements ActionHydrator {

    protected Map<String, String> getBaseParameters(AnomalyTarget target) {
        Map<String, String> params = new HashMap<>();
        params.put("namespace", ImageRemediationPlanner.parseNamespaceFromDeploymentIri(target.resourceIri()));
        params.put("resourceName", target.resourceName());
        return params;
    }
}
