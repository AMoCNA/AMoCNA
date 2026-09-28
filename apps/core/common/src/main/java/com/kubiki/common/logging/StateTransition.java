package com.kubiki.common.logging;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to declaratively transition the state of a Petri Net resource in GraphDB.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface StateTransition {
    String from() default "";        // Optional initial state fragment
    String to();                     // Target state fragment on successful completion
    String onError() default "";     // Target state fragment on method exception
    String targetExpression();       // SpEL expression to resolve the target resource IRI
}
