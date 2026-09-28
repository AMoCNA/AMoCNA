package com.kubiki.palamedes.pipeline.pipes.rules;

import com.kubiki.palamedes.model.WorkflowState;
import com.kubiki.palamedes.model.WorkflowStateMapper;
import com.kubiki.palamedes.pipeline.WorkflowContext;
import org.eclipse.rdf4j.model.IRI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TenancyDependencySpecification implements Specification<SchedulingTarget> {
    private static final Logger log = LoggerFactory.getLogger(TenancyDependencySpecification.class);

    private final WorkflowStateMapper stateMapper;

    @Autowired
    public TenancyDependencySpecification(WorkflowStateMapper stateMapper) {
        this.stateMapper = stateMapper;
    }

    @Override
    public boolean isSatisfiedBy(SchedulingTarget target) {
        WorkflowContext context = target.context();
        SchedulingState state = target.state();

        IRI currentActionId = context.actionId();
        IRI currentTarget = context.actionData().target();

        if (currentTarget == null) {
            return true;
        }

        boolean blockedByDependency = state.activeSummaries().stream()
                .filter(active -> !active.actionIri().equals(currentActionId))
                .filter(active -> {
                    try {
                        WorkflowState ws = stateMapper.fromFragment(active.stateFragment());
                        return ws == WorkflowState.IN_PROGRESS;
                    } catch (IllegalArgumentException e) {
                        log.warn("TenancyDependencySpecification: Unknown state fragment: {}", active.stateFragment());
                        return false;
                    }
                })
                .anyMatch(active -> {
                    boolean isDep = state.dependencyChecker() != null
                            && state.dependencyChecker().test(currentTarget, active.resourceIri());
                    if (isDep) {
                        log.info("OptimizationPipe: Action {} targeting {} is postponed because dependency resource {} is executing action {}",
                                currentActionId, currentTarget, active.resourceIri(), active.actionIri());
                    }
                    return isDep;
                });

        return !blockedByDependency;
    }
}

