package com.kubiki.themis.aspect;

import com.kubiki.common.model.ActionStatusUpdate;
import com.kubiki.common.model.ExecutionStatus;
import com.kubiki.themis.config.ThemisProperties;
import com.kubiki.themis.execution.StatusProducer;
import com.kubiki.themis.model.ExecutionResult;
import com.kubiki.themis.policy.ConditionEvaluator;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Aspect
@Component
public class ActionVerificationAspect {
    private static final Logger log = LoggerFactory.getLogger(ActionVerificationAspect.class);

    private final ConditionEvaluator conditionEvaluator;
    private final StatusProducer statusProducer;
    private final ThemisProperties properties;

    public ActionVerificationAspect(ConditionEvaluator conditionEvaluator, StatusProducer statusProducer, ThemisProperties properties) {
        this.conditionEvaluator = conditionEvaluator;
        this.statusProducer = statusProducer;
        this.properties = properties;
    }

    @Around("@annotation(com.kubiki.common.logging.PreVerify)")
    public Object verifyPreconditions(ProceedingJoinPoint joinPoint) throws Throwable {
        Object[] args = joinPoint.getArgs();
        String actionId = extractActionId(args);

        if (actionId != null) {
            log.info("Aspect: Verifying pre-conditions for action {}", actionId);
            if (!conditionEvaluator.evaluatePreConditions(actionId)) {
                log.warn("Aspect: Pre-conditions failed for action: {}", actionId);
                statusProducer.sendUpdate(new ActionStatusUpdate(actionId, ExecutionStatus.FAILED_INTERNAL, "Pre-condition failed", 0));
                return ExecutionResult.failure(0, "Pre-condition failed", ExecutionStatus.FAILED_INTERNAL);
            }
        }

        return joinPoint.proceed();
    }

    @Around("@annotation(com.kubiki.common.logging.PostVerify)")
    public Object verifyPostconditions(ProceedingJoinPoint joinPoint) throws Throwable {
        Object[] args = joinPoint.getArgs();
        String actionId = extractActionId(args);

        Object executionResult = joinPoint.proceed();

        if (actionId != null && executionResult instanceof ExecutionResult result) {
            if (result.success()) {
                int delay = properties.execution() != null ? properties.execution().postConditionDelayMs() : 0;
                if (delay > 0) {
                    try {
                        log.info("Aspect: Waiting {}ms for cluster stabilization before post-condition verification", delay);
                        Thread.sleep(delay);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.error("Aspect: Stabilization delay interrupted", e);
                    }
                }

                log.info("Aspect: Verifying post-conditions for action {}", actionId);
                if (!conditionEvaluator.evaluatePostConditions(actionId)) {
                    log.warn("Aspect: Post-condition verification failed for action: {}", actionId);
                    result = ExecutionResult.failure(result.observedStatusCode(), "Post-condition verification failed", ExecutionStatus.FAILED_INTERNAL);
                }
            }

            ActionStatusUpdate status = new ActionStatusUpdate(
                    actionId,
                    result.status(),
                    result.errorMessage(),
                    result.observedStatusCode()
            );

            statusProducer.sendUpdate(status);
            log.info("Aspect: Sent status update for action {}: {}", actionId, result.status());
            return result;
        }

        return executionResult;
    }

    private String extractActionId(Object[] args) {
        if (args == null) return null;
        for (Object arg : args) {
            if (arg == null) continue;
            if (arg instanceof String) {
                return (String) arg;
            }
            try {
                Method m = arg.getClass().getMethod("actionId");
                return (String) m.invoke(arg);
            } catch (Exception e) {
                try {
                    Method m = arg.getClass().getMethod("getActionId");
                    return (String) m.invoke(arg);
                } catch (Exception ex) {
                    // Ignore
                }
            }
        }
        return null;
    }
}
