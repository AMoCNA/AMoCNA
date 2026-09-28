package com.kubiki.common.logging;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Aspect handling the `@LogLoopStep` annotation.
 * Automatically handles SpEL parsing, MDC correlation propagation, execution timing,
 * and unified structured logging for the MAPE-K loop.
 */
@Aspect
@Component
public class LogLoopStepAspect {

    private static final Logger log = LoggerFactory.getLogger(LogLoopStepAspect.class);
    private final ExpressionParser parser = new SpelExpressionParser();

    @Around("@annotation(logLoopStep)")
    public Object logStep(ProceedingJoinPoint joinPoint, LogLoopStep logLoopStep) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // 1. Build SpEL Context from method parameters
        StandardEvaluationContext context = new StandardEvaluationContext();
        String[] parameterNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();
        if (parameterNames != null && args != null) {
            for (int i = 0; i < parameterNames.length; i++) {
                if (i < args.length) {
                    context.setVariable(parameterNames[i], args[i]);
                }
            }
        }

        // 2. Resolve parameters using SpEL or MDC fallback
        String correlationId = resolveValue(logLoopStep.correlationId(), context);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = MDC.get("correlationId");
        }
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = "N/A";
        }

        String actionId = resolveValue(logLoopStep.actionId(), context);
        if (actionId == null || actionId.isBlank()) {
            actionId = MDC.get("actionId");
        }
        if (actionId == null || actionId.isBlank()) {
            actionId = "N/A";
        }

        String resource = resolveValue(logLoopStep.resource(), context);
        if (resource == null || resource.isBlank()) {
            resource = MDC.get("resourceName");
        }
        if (resource == null || resource.isBlank()) {
            resource = MDC.get("resourceIri");
        }
        if (resource == null || resource.isBlank()) {
            resource = "N/A";
        }

        String details = resolveValue(logLoopStep.details(), context);
        if (details == null) {
            details = "";
        }

        String phaseStr = logLoopStep.phase().name();
        String stepName = logLoopStep.step();

        // 3. Temporarily set values in MDC if they are valid
        Map<String, String> previousMdcValues = new HashMap<>();
        setMdcIfValid("correlationId", correlationId, previousMdcValues);
        setMdcIfValid("actionId", actionId, previousMdcValues);
        setMdcIfValid("resourceName", resource, previousMdcValues);
        setMdcIfValid("loop.phase", phaseStr, previousMdcValues);
        setMdcIfValid("loop.step", stepName, previousMdcValues);

        long start = System.currentTimeMillis();
        boolean isDebug = logLoopStep.debugOnly();

        // 4. Log start
        String startMsg = stepName + " started";
        if (details != null && !details.isBlank()) {
            startMsg += " (" + details + ")";
        }
        if (isDebug) {
            log.debug(startMsg);
        } else {
            log.info(startMsg);
        }

        try {
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - start;

            // Log success
            String successMsg = stepName + " succeeded in " + duration + "ms";
            if (isDebug) {
                log.debug(successMsg);
            } else {
                log.info(successMsg);
            }

            return result;
        } catch (Throwable t) {
            long duration = System.currentTimeMillis() - start;

            // Log failure
            String failureMsg = stepName + " failed in " + duration + "ms: " + t.getMessage();
            log.error(failureMsg, t);

            throw t;
        } finally {
            // Restore MDC context
            for (Map.Entry<String, String> entry : previousMdcValues.entrySet()) {
                if (entry.getValue() == null) {
                    MDC.remove(entry.getKey());
                } else {
                    MDC.put(entry.getKey(), entry.getValue());
                }
            }
        }
    }

    private String resolveValue(String expressionStr, StandardEvaluationContext context) {
        if (expressionStr == null || expressionStr.isBlank()) {
            return null;
        }
        try {
            Expression expression = parser.parseExpression(expressionStr);
            Object value = expression.getValue(context);
            return value != null ? value.toString() : null;
        } catch (Exception e) {
            log.warn("Failed to evaluate SpEL expression: {}", expressionStr, e);
            return null;
        }
    }

    private void setMdcIfValid(String key, String value, Map<String, String> previousValues) {
        if (value != null && !"N/A".equals(value)) {
            previousValues.put(key, MDC.get(key));
            MDC.put(key, value);
        }
    }
}
