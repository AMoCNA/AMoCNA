package com.kubiki.common.logging;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to declaratively enforce idempotency and cooldown periods for planned actions.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface IdempotentAction {
    String targetExpression(); // SpEL expression to extract target resource IRI
    String intentExpression(); // SpEL expression to extract intent IRI
    int cooldownSeconds() default 300; // Default cooldown period
}
