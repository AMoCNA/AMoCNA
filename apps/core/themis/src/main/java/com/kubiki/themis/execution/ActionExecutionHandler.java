package com.kubiki.themis.execution;

import com.kubiki.common.logging.LogLoopStep;
import com.kubiki.common.logging.LoopPhase;
import com.kubiki.common.model.ActionMessage;
import com.kubiki.common.model.ExecutionStatus;
import com.kubiki.themis.model.ExecutionResult;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.kubiki.common.logging.PreVerify;
import com.kubiki.common.logging.PostVerify;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ActionExecutionHandler {
    private static final Logger log = LoggerFactory.getLogger(ActionExecutionHandler.class);
    private final ExecutorFactory executorFactory;

    @PreVerify
    @PostVerify
    @LogLoopStep(
        phase = LoopPhase.EXECUTE,
        step = "Execute Action & Verify",
        actionId = "#message.actionId()",
        details = "'protocol=' + #message.protocol() + ', isIdempotent=' + #message.isIdempotent() + ', maxRetries=' + #message.maxRetries()"
    )
    public ExecutionResult executeAndVerify(ActionMessage message) {
        ProtocolExecutor executor = executorFactory.getExecutor(message.protocol())
                .orElse(null);

        if (executor == null) {
            log.error("No executor found for protocol: {}", message.protocol());
            return ExecutionResult.failure(0, "No executor for protocol", ExecutionStatus.FAILED_INTERNAL);
        }

        return executeWithRetry(executor, message);
    }

    private ExecutionResult executeWithRetry(ProtocolExecutor executor, ActionMessage message) {
        int maxAttempts = message.isIdempotent() ? Math.max(1, message.maxRetries() + 1) : 1;

        RetryTemplate retryTemplate = RetryTemplate.builder()
                .maxAttempts(maxAttempts)
                .fixedBackoff(1000) // 1 second backoff
                .retryOn(RuntimeException.class)
                .build();

        try {
            return retryTemplate.execute(context -> {
                if (context.getRetryCount() > 0) {
                    log.info("Retry attempt {} for action {}", context.getRetryCount(), message.actionId());
                }
                ExecutionResult result = executor.executeStateless(message);

                if (!result.success() && isRetryable(result.status()) && context.getRetryCount() < maxAttempts - 1) {
                    throw new RuntimeException("Execution failed, triggering retry: " + result.errorMessage());
                }
                return result;
            });
        } catch (Exception e) {
            log.error("Execution failed after all retries for action {}: {}", message.actionId(), e.getMessage());
            return ExecutionResult.failure(500, e.getMessage(), ExecutionStatus.FAILED_INTERNAL);
        }
    }

    private boolean isRetryable(ExecutionStatus status) {
        return status == ExecutionStatus.FAILED_TIMEOUT || status == ExecutionStatus.FAILED_INTERNAL;
    }
}
