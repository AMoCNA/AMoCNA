package com.kubiki.palamedes.pipeline.pipes.rules;

import com.kubiki.palamedes.model.ActionData;
import com.kubiki.palamedes.model.ActiveActionSummary;
import com.kubiki.palamedes.model.IntentMetadata;
import org.eclipse.rdf4j.model.IRI;

import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

public record SchedulingState(
        List<ActiveActionSummary> activeSummaries,
        Map<IRI, ActionData> structures,
        Map<IRI, IntentMetadata> intentMetadata,
        Map<IRI, Double> dynamicCosts,
        Map<IRI, Double> densities,
        Map<IRI, Integer> intentConcurrentCounts,
        double infrastructureCapacity,
        double containerizationCapacity,
        double applicationCapacity,
        Map<String, Double> remainingCapacities,
        BiPredicate<IRI, IRI> dependencyChecker
) {
    public record ResourcePair(IRI source, IRI target) {}
}
