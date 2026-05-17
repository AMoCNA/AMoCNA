package com.kubiki.palamedes.pipeline;

import com.kubiki.palamedes.analyzer.AnomalyAgent;
import com.kubiki.palamedes.knowledge.GraphDBGateway;
import com.kubiki.palamedes.model.ActionData;
import com.kubiki.palamedes.model.ActiveActionSummary;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MapePipeline {
    private static final Logger log = LoggerFactory.getLogger(MapePipeline.class);
    private final List<MapePipe> pipes;
    private final GraphDBGateway graphDBGateway;
    private final AnomalyAgent anomalyAgent;


    @Scheduled(fixedRateString = "${palamedes.engine.pipeline-rate-ms}")
    public void run() {
        log.debug("Starting MAPE Pipeline run...");

        // 1. MAPE-Analyze: Detect new anomalies and create INITIAL actions
        // This is the "Inlet" for new workflows
        anomalyAgent.analyze();

        // 2. Fetch all non-terminal actions from GraphDB
        List<ActiveActionSummary> activeActions = graphDBGateway.findActiveActions();
        log.debug("Found {} active actions in the Petri Net", activeActions.size());

        for (ActiveActionSummary action : activeActions) {
            try {
                // Fetch the full structure for the context
                ActionData data = graphDBGateway.fetchActionStructure(action.actionIri());
                if (data == null) {
                    log.warn("Could not load structure for action {}, skipping", action.actionIri());
                    continue;
                }

                WorkflowContext context = new WorkflowContext(action.actionIri(), data);
                // metadata enrichment
                context.metadata().put("resourceName", action.resourceName());
                context.metadata().put("currentState", action.stateFragment());

                // Execute the pipe chain
                for (MapePipe pipe : pipes) {
                    if (!pipe.process(context)) {
                        log.debug("Pipeline stopped at {} for action {}", pipe.getClass().getSimpleName(), action.actionIri());
                        break;
                    }
                }
            } catch (Exception e) {
                log.error("Error processing action {} in pipeline: {}", action.actionIri(), e.getMessage(), e);
            }
        }
    }
}
