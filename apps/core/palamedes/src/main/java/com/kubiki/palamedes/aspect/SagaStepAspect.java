package com.kubiki.palamedes.aspect;

import com.kubiki.common.logging.SagaStep;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Aspect
@Component
public class SagaStepAspect {

    private static final Logger log = LoggerFactory.getLogger(SagaStepAspect.class);

    @Around("@annotation(sagaStep)")
    public Object manageSagaStep(ProceedingJoinPoint joinPoint, SagaStep sagaStep) throws Throwable {
        log.info("SagaStep Aspect: Intercepting step: '{}'", sagaStep.name());

        int maxAttempts = Math.max(1, sagaStep.maxRetries() + 1);
        long backoffMs = sagaStep.backoffMs();

        Object result = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            if (attempt > 1) {
                log.info("SagaStep Aspect: Retrying step '{}' (attempt {}/{}) after backoff of {}ms",
                        sagaStep.name(), attempt, maxAttempts, backoffMs);
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw ie;
                }
            }

            try {
                result = joinPoint.proceed();

                if (result != null) {
                    try {
                        Method successMethod = result.getClass().getMethod("success");
                        boolean success = (boolean) successMethod.invoke(result);
                        if (!success) {
                            if (attempt < maxAttempts) {
                                log.warn("SagaStep Aspect: Step '{}' returned a failure result on attempt {}/{}. Retrying...",
                                        sagaStep.name(), attempt, maxAttempts);
                                continue; // Trigger retry
                            } else {
                                log.warn("SagaStep Aspect: Step '{}' returned a failure result after all attempts ({}). Triggering compensation: '{}'",
                                        sagaStep.name(), maxAttempts, sagaStep.compensationMethod());
                                invokeCompensation(joinPoint, sagaStep.compensationMethod());
                            }
                        }
                    } catch (NoSuchMethodException e) {
                        // Result doesn't have a success method, assume success since no exception was thrown
                    }
                }
                return result;
            } catch (Throwable t) {
                boolean shouldRetry = isRetryableException(t, sagaStep.retryOn());

                if (shouldRetry && attempt < maxAttempts) {
                    log.warn("SagaStep Aspect: Step '{}' failed with exception: {} on attempt {}/{}. Retrying...",
                            sagaStep.name(), t.getMessage(), attempt, maxAttempts);
                } else {
                    log.error("SagaStep Aspect: Step '{}' failed permanently after all attempts ({}) due to: {}. Triggering compensation: '{}'",
                            sagaStep.name(), maxAttempts, t.getMessage(), sagaStep.compensationMethod());
                    invokeCompensation(joinPoint, sagaStep.compensationMethod());
                    throw t;
                }
            }
        }

        return result;
    }

    private boolean isRetryableException(Throwable t, Class<? extends Throwable>[] retryOn) {
        for (Class<? extends Throwable> retryableClass : retryOn) {
            if (retryableClass.isAssignableFrom(t.getClass())) {
                return true;
            }
        }
        return false;
    }

    private void invokeCompensation(ProceedingJoinPoint joinPoint, String compensationMethodName) {
        Object target = joinPoint.getTarget();
        Object[] args = joinPoint.getArgs();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Class<?>[] parameterTypes = signature.getParameterTypes();

        try {
            Method compensationMethod = target.getClass().getMethod(compensationMethodName, parameterTypes);
            compensationMethod.setAccessible(true);
            log.info("SagaStep Aspect: Invoking compensation method '{}' on target '{}'", compensationMethodName, target.getClass().getSimpleName());
            compensationMethod.invoke(target, args);
        } catch (Exception e) {
            log.error("SagaStep Aspect: Failed to invoke compensation method '{}': {}", compensationMethodName, e.getMessage(), e);
        }
    }
}
