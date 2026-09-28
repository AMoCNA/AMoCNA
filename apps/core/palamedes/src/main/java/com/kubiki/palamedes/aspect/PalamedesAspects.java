package com.kubiki.palamedes.aspect;

import com.kubiki.common.logging.IdempotentAction;
import com.kubiki.common.logging.StateTransition;
import com.kubiki.palamedes.knowledge.GraphDBGateway;
import com.kubiki.palamedes.knowledge.StateRepository;
import com.kubiki.palamedes.model.WorkflowState;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class PalamedesAspects {

    private static final Logger log = LoggerFactory.getLogger(PalamedesAspects.class);

    private final GraphDBGateway gateway;
    private final StateRepository stateRepository;

    public PalamedesAspects(GraphDBGateway gateway, StateRepository stateRepository) {
        this.gateway = gateway;
        this.stateRepository = stateRepository;
    }

    @Around("@annotation(stateTransition)")
    public Object transitionState(ProceedingJoinPoint joinPoint, StateTransition stateTransition) throws Throwable {
        Object targetObj = evaluateSpel(joinPoint, stateTransition.targetExpression());
        IRI targetIri = resolveIri(targetObj);

        if (targetIri == null) {
            log.warn("StateTransition aspect: Could not resolve target IRI from expression: {}", stateTransition.targetExpression());
            return joinPoint.proceed();
        }

        try {
            Object result = joinPoint.proceed();

            // On success, transition to the target state
            WorkflowState toState = resolveWorkflowState(stateTransition.to());
            if (toState != null) {
                if (!stateTransition.from().isEmpty()) {
                    WorkflowState fromState = resolveWorkflowState(stateTransition.from());
                    if (fromState != null) {
                        log.info("Aspect Transitioning action {} from {} to {}", targetIri, fromState, toState);
                        stateRepository.transition(targetIri, fromState, toState);
                    }
                } else {
                    log.info("Aspect Transitioning action {} to {}", targetIri, toState);
                    gateway.transitionState(targetIri, stateTransition.to());
                }
            }
            return result;
        } catch (Throwable t) {
            // On error, transition to the error state if defined
            if (!stateTransition.onError().isEmpty()) {
                WorkflowState errorState = resolveWorkflowState(stateTransition.onError());
                if (errorState != null) {
                    log.info("Aspect Transitioning action {} to error state {} due to: {}", targetIri, errorState, t.getMessage());
                    if (!stateTransition.from().isEmpty()) {
                        WorkflowState fromState = resolveWorkflowState(stateTransition.from());
                        if (fromState != null) {
                            stateRepository.transition(targetIri, fromState, errorState);
                        }
                    } else {
                        gateway.transitionState(targetIri, stateTransition.onError());
                    }
                }
            }
            throw t;
        }
    }

    @Around("@annotation(idempotentAction)")
    public Object enforceIdempotency(ProceedingJoinPoint joinPoint, IdempotentAction idempotentAction) throws Throwable {
        Object targetObj = evaluateSpel(joinPoint, idempotentAction.targetExpression());
        Object intentObj = evaluateSpel(joinPoint, idempotentAction.intentExpression());

        IRI targetIri = resolveIri(targetObj);
        IRI intentIri = resolveIri(intentObj);

        if (targetIri != null && intentIri != null) {
            if (!gateway.isIdempotencyWindowOpen(targetIri, intentIri)) {
                log.info("Aspect: Action for target {} and intent {} is blocked by idempotency cooldown, skipping execution",
                        targetIri, intentIri);

                // Return default/void/empty depending on method signature
                MethodSignature signature = (MethodSignature) joinPoint.getSignature();
                Class<?> returnType = signature.getReturnType();
                if (returnType.equals(Void.TYPE)) {
                    return null;
                } else if (returnType.equals(Boolean.TYPE)) {
                    return false;
                } else {
                    return null;
                }
            }
        }

        return joinPoint.proceed();
    }

    private Object evaluateSpel(ProceedingJoinPoint joinPoint, String expressionStr) {
        if (expressionStr == null || expressionStr.isBlank()) return null;
        ExpressionParser parser = new SpelExpressionParser();
        StandardEvaluationContext context = new StandardEvaluationContext();

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] parameterNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();

        if (parameterNames != null) {
            for (int i = 0; i < parameterNames.length; i++) {
                context.setVariable(parameterNames[i], args[i]);
            }
        }

        try {
            Expression expression = parser.parseExpression(expressionStr);
            return expression.getValue(context);
        } catch (Exception e) {
            log.error("Failed to parse SpEL expression: {}", expressionStr, e);
            return null;
        }
    }

    private IRI resolveIri(Object obj) {
        if (obj == null) return null;
        if (obj instanceof IRI) return (IRI) obj;
        if (obj instanceof String) {
            try {
                return SimpleValueFactory.getInstance().createIRI((String) obj);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private WorkflowState resolveWorkflowState(String stateFragment) {
        try {
            String clean = stateFragment.toUpperCase();
            if (clean.startsWith("STATE_")) {
                clean = clean.substring(6);
            }
            return WorkflowState.valueOf(clean);
        } catch (Exception e) {
            return null;
        }
    }
}
