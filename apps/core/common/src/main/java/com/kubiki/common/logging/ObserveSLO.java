package com.kubiki.common.logging;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to declaratively observe SLO metrics and raise warnings when latency thresholds are exceeded.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ObserveSLO {
    String name();
    long thresholdMs() default 60000; // Warning threshold in milliseconds
    String[] tags() default {};
}
