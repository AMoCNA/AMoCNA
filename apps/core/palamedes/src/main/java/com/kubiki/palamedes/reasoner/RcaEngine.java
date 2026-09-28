package com.kubiki.palamedes.reasoner;

import com.kubiki.palamedes.knowledge.GraphDBGateway;
import com.kubiki.palamedes.model.AnomalyTarget;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RcaEngine {
    private final GraphDBGateway gateway;

    /**
     * Performs Root Cause Analysis starting from the detected anomaly.
     * Returns the target representing the deepest root cause.
     */
    @Timed(value = "palamedes.reasoner.rca", description = "Time taken to perform root cause analysis")
    public AnomalyTarget findRootCause(AnomalyTarget initialAnomaly) {
        List<AnomalyTarget> rootCauses = gateway.findRootCause(initialAnomaly.resourceIri());

        // For simplicity, if multiple paths exist, just return the one that is NOT the initial anomaly itself
        // if we found one deeper. Otherwise return the initial.
        return rootCauses.stream()
                .filter(target -> !target.resourceIri().equals(initialAnomaly.resourceIri()))
                .findFirst()
                .orElse(initialAnomaly);
    }
}
