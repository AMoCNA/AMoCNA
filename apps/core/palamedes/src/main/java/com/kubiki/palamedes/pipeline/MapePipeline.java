package com.kubiki.palamedes.pipeline;

import com.kubiki.common.logging.MdcPropagatingExecutor;
import com.kubiki.palamedes.config.PalamedesProperties;
import com.kubiki.palamedes.knowledge.GraphDBGateway;
import com.kubiki.palamedes.model.ActionData;
import com.kubiki.palamedes.model.ActiveActionSummary;
import lombok.RequiredArgsConstructor;
import org.eclipse.rdf4j.model.IRI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

@Service
@RequiredArgsConstructor
public class MapePipeline {
    private static final Logger log = LoggerFactory.getLogger(MapePipeline.class);
    private final List<MapePipe> pipes;
    private final GraphDBGateway graphDBGateway;
    private final PalamedesProperties palamedesProperties;
    private final ReentrantLock runLock = new ReentrantLock();


    @EventListener(EngineWakeupEvent.class)
    @Scheduled(fixedRateString = "${palamedes.engine.fallback-pipeline-rate-ms}")
    public void run() {
        if (!runLock.tryLock()) {
            log.debug("MapePipeline.run() already in progress, skipping trigger");
            return;
        }
        try {
            log.info("MapePipeline.run() triggered");

            List<ActiveActionSummary> activeActions = graphDBGateway.findActiveActions();
            if (activeActions.isEmpty()) {
                log.info("MapePipeline: No active actions found in the Petri Net. Pipeline run finished.");
                return;
            }
            log.info("MapePipeline: Found {} active actions in the Petri Net", activeActions.size());

            int batchSize = palamedesProperties.engine().batchSize();
            List<List<ActiveActionSummary>> batches = partition(activeActions, batchSize);
            log.info("MapePipeline: Partitioned active actions into {} batches", batches.size());

            try (var executor = MdcPropagatingExecutor.newVirtualThreadPerTaskExecutor()) {
                for (List<ActiveActionSummary> batch : batches) {
                    executor.submit(() -> processBatch(batch));
                }
            } catch (Exception e) {
                log.error("Error in MAPE Pipeline virtual thread execution: {}", e.getMessage(), e);
            }
        } finally {
            runLock.unlock();
        }
    }

    private void processBatch(List<ActiveActionSummary> batch) {
        log.info("MapePipeline: Processing batch of size {}", batch.size());
        List<IRI> iris = batch.stream().map(ActiveActionSummary::actionIri).toList();

        // 1. Batch fetch all ActionData structures
        Map<IRI, ActionData> structures = graphDBGateway.fetchActionStructures(iris);

        // 2. Batch fetch all action hydrations
        Map<IRI, Map<String, String>> hydrations = graphDBGateway.fetchActionHydrations(iris);

        // 3. Batch fetch all idempotency states
        Map<IRI, Boolean> idempotencyStates = graphDBGateway.fetchIdempotencyStates(iris);

        for (ActiveActionSummary action : batch) {
            MDC.put("actionId", action.actionIri().stringValue());
            MDC.put("resourceName", action.resourceName());
            MDC.put("currentState", action.stateFragment());
            try {
                ActionData data = structures.get(action.actionIri());
                if (data == null) {
                    log.warn("MapePipeline: Could not load structure for action, skipping");
                    continue;
                }

                log.info("MapePipeline: Creating WorkflowContext for action on resource");
                WorkflowContext context = new WorkflowContext(action.actionIri(), data);
                context.metadata().put("resourceName", action.resourceName());
                context.metadata().put("currentState", action.stateFragment());
                context.metadata().put("idempotencyOpen", idempotencyStates.getOrDefault(action.actionIri(), true));

                Map<String, String> actionHydration = hydrations.getOrDefault(action.actionIri(), Map.of());
                context.metadata().putAll(actionHydration);

                for (MapePipe pipe : pipes) {
                    log.info("MapePipeline: Invoking pipe {}", pipe.getClass().getSimpleName());
                    if (!pipe.process(context)) {
                        log.info("MapePipeline: Pipeline stopped at {}", pipe.getClass().getSimpleName());
                        break;
                    }
                }
            } catch (Exception e) {
                log.error("Error processing action in pipeline: {}", e.getMessage(), e);
            } finally {
                MDC.clear();
            }
        }
    }

    private <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }
}
