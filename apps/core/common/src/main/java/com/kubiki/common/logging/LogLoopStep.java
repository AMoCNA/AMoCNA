package com.kubiki.common.logging;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to declaratively mark methods participating in the MAPE-K autonomic loop.
 * Enables unified logging, tracking, and trace correlation across microservices.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LogLoopStep {
    /**
     * The phase of the MAPE-K loop.
     */
    LoopPhase phase();

    /**
     * The name/description of this specific step.
     */
    String step();

    /**
     * SpEL expression to extract the correlation ID (e.g. from parameters).
     */
    String correlationId() default "";

    /**
     * SpEL expression to extract the action ID.
     */
    String actionId() default "";

    /**
     * SpEL expression to extract the resource name or IRI.
     */
    String resource() default "";

    /**
     * SpEL expression to extract additional details to append to the log.
     */
    String details() default "";

    /**
     * Whether this step should only log at DEBUG level instead of INFO.
     */
    boolean debugOnly() default false;
}
