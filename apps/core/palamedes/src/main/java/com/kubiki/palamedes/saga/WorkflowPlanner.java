package com.kubiki.palamedes.saga;

import com.kubiki.common.ontology.OntologyRegistry;
import com.kubiki.palamedes.knowledge.GraphDBGateway;
import com.kubiki.palamedes.model.ActionData;
import com.kubiki.palamedes.model.WorkflowState;
import com.kubiki.palamedes.model.WorkflowStateMapper;
import com.kubiki.palamedes.utils.ActionUtils;
import lombok.RequiredArgsConstructor;
import org.eclipse.rdf4j.model.IRI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * WorkflowPlanner:
 * Responsible for decomposing ComplexWorkflows into sequential steps in the graph.
 */
@Service
@RequiredArgsConstructor
public class WorkflowPlanner {
    private static final Logger log = LoggerFactory.getLogger(WorkflowPlanner.class);

    private final ActionUtils utils;
    private final GraphDBGateway graphDBGateway;
    private final OntologyRegistry ontologyRegistry;
    private final WorkflowStateMapper mapper;

    public void planWorkflow(ActionData.ComplexWorkflow workflow, IRI parentIri) {
        log.info("Decomposing ComplexWorkflow {} into steps", parentIri);
        List<ActionData> steps = workflow.steps();
        IRI previousStepIri = null;

        for (ActionData step : steps) {
            String stepId = utils.generateStepId();
            IRI stepIri = ontologyRegistry.actionsOntology(stepId);

            graphDBGateway.materializeActionInstance(stepIri, step, workflow.target(), parentIri);

            if (previousStepIri != null) {
                graphDBGateway.linkDependent(stepIri, previousStepIri);
            } else {
                graphDBGateway.transitionState(stepIri, mapper.getFragment(WorkflowState.INITIAL));
            }

            previousStepIri = stepIri;
        }
    }
}
