package com.kubiki.palamedes.pipeline.pipes.rules;

import com.kubiki.palamedes.config.PalamedesProperties;
import com.kubiki.palamedes.model.ActionData;
import com.kubiki.palamedes.model.IntentMetadata;
import com.kubiki.palamedes.model.WorkflowState;
import com.kubiki.palamedes.model.WorkflowStateMapper;
import com.kubiki.palamedes.pipeline.WorkflowContext;
import org.eclipse.rdf4j.model.IRI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class KnapsackBudgetSpecification implements Specification<SchedulingTarget> {
    private static final Logger log = LoggerFactory.getLogger(KnapsackBudgetSpecification.class);

    private static final float DEFAULT_RISK_MULTIPLIER = 1.0f;
    private static final int CONCURRENT_INCREMENT = 1;

    private final PalamedesProperties palamedesProperties;
    private final WorkflowStateMapper stateMapper;

    @Autowired
    public KnapsackBudgetSpecification(PalamedesProperties palamedesProperties, WorkflowStateMapper stateMapper) {
        this.palamedesProperties = palamedesProperties;
        this.stateMapper = stateMapper;
    }

    @Override
    public boolean isSatisfiedBy(SchedulingTarget target) {
        WorkflowContext context = target.context();
        SchedulingState state = target.state();

        IRI currentActionId = context.actionId();
        ActionData currentAction = context.actionData();

        // Get all planned actions
        List<ActionData> plannedActions = state.activeSummaries().stream()
                .filter(active -> {
                    try {
                        WorkflowState ws = stateMapper.fromFragment(active.stateFragment());
                        return ws == WorkflowState.PLANNED;
                    } catch (IllegalArgumentException e) {
                        log.warn("KnapsackBudgetSpecification: Unknown state fragment: {}", active.stateFragment());
                        return false;
                    }
                })
                .map(active -> state.structures().get(active.actionIri()))
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(ArrayList::new));


        // Ensure our current action is in the planned list
        if (plannedActions.stream().noneMatch(a -> a.id().equals(currentActionId))) {
            plannedActions.add(currentAction);
        }

        // Sort by density descending
        plannedActions.sort((a, b) -> Double.compare(state.densities().get(b.id()), state.densities().get(a.id())));

        // Track remaining capacity during greedy scheduling
        Map<String, Double> remainingCapacities = new HashMap<>(state.remainingCapacities());
        Map<IRI, Integer> concurrentCounts = new HashMap<>(state.intentConcurrentCounts());

        for (ActionData action : plannedActions) {
            double dynCost = state.dynamicCosts().get(action.id());
            String layer = action.layerBoundary() != null ? action.layerBoundary().getLocalName() : "";
            IntentMetadata metadata = state.intentMetadata().getOrDefault(action.functionalIntent(),
                    new IntentMetadata(action.functionalIntent(), DEFAULT_RISK_MULTIPLIER, Integer.MAX_VALUE, true));
            int currentConcurrentCount = concurrentCounts.getOrDefault(action.functionalIntent(), 0);

            // Generic lookup
            String matchedLayerKey = remainingCapacities.keySet().stream()
                    .filter(layerKey -> layer.contains(layerKey))
                    .findFirst()
                    .orElse("Application");

            double remainingCap = remainingCapacities.getOrDefault(matchedLayerKey, 0.0);
            boolean layerBudgetFits = (dynCost <= remainingCap);
            boolean cardinalityFits = (currentConcurrentCount < metadata.cardinalityCap());

            if (layerBudgetFits && cardinalityFits) {
                // Deduct capacity
                remainingCapacities.put(matchedLayerKey, remainingCap - dynCost);
                concurrentCounts.merge(action.functionalIntent(), CONCURRENT_INCREMENT, Integer::sum);

                if (action.id().equals(currentActionId)) {
                    log.info("OptimizationPipe: Scheduled and approved action {} (Layer: {}, Dynamic Cost: {}, Density: {})",
                            currentActionId, layer, dynCost, state.densities().get(currentActionId));
                    return true;
                }
            } else {
                if (action.id().equals(currentActionId)) {
                    log.info("OptimizationPipe: Action {} could not be scheduled. Layer budget fits: {}, Cardinality fits: {}. Postponing.",
                            currentActionId, layerBudgetFits, cardinalityFits);
                    return false;
                }
            }
        }

        return false;
    }
}
