package com.kubiki.palamedes.saga;

import com.kubiki.palamedes.knowledge.ActionRepository;
import com.kubiki.palamedes.model.ActionData;
import com.kubiki.palamedes.model.ActiveActionSummary;
import lombok.RequiredArgsConstructor;
import org.eclipse.rdf4j.model.IRI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
public class SagaWatchdog {
    private static final Logger log = LoggerFactory.getLogger(SagaWatchdog.class);

    private static final String STATE_IN_PROGRESS_FRAGMENT = "State_InProgress";
    private static final String STATUS_FAILED_TIMEOUT = "FAILED_TIMEOUT";

    private final ActionRepository actionRepository;
    private final SagaTransitionHandler transitionHandler;

    @Value("${palamedes.saga.default-timeout-seconds:30}")
    private int defaultTimeoutSeconds;

    @Scheduled(fixedDelayString = "${palamedes.saga.watchdog-interval-ms:5000}")
    public void monitorWorkflows() {
        log.debug("SagaWatchdog: Scanning for timed out actions...");
        List<ActiveActionSummary> activeActions;
        try {
            activeActions = actionRepository.findActiveActions();
        } catch (Exception e) {
            log.error("SagaWatchdog: Failed to query active actions", e);
            return;
        }

        for (ActiveActionSummary active : activeActions) {
            if (STATE_IN_PROGRESS_FRAGMENT.equals(active.stateFragment())) {
                IRI actionIri = active.actionIri();
                Instant lastTransition = actionRepository.getLastTransitionTimestamp(actionIri);
                if (lastTransition == null) {
                    continue;
                }

                ActionData actionData = actionRepository.fetchActionStructure(actionIri);
                int timeout = defaultTimeoutSeconds;
                if (actionData instanceof ActionData.SimpleAction simpleAction) {
                    if (simpleAction.timeoutSeconds() > 0) {
                        timeout = simpleAction.timeoutSeconds();
                    }
                }

                Duration elapsed = Duration.between(lastTransition, Instant.now());
                if (elapsed.toSeconds() > timeout) {
                    log.warn("SagaWatchdog: Action {} has timed out (elapsed: {}s, limit: {}s). Initiating compensation.",
                            actionIri, elapsed.toSeconds(), timeout);

                    try {
                        actionRepository.updateExecutionStatus(actionIri, STATUS_FAILED_TIMEOUT);
                        
                        // Process the failure transitions (automatically transitions state via Aspect)
                        transitionHandler.processFailureTransition(actionIri);
                    } catch (Exception e) {
                        log.error("SagaWatchdog: Failed to timeout action {}", actionIri, e);
                    }
                }
            }
        }
    }
}
