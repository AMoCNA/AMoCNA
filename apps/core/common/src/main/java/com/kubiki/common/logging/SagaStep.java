package com.kubiki.common.logging;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to declaratively bind execution steps inside autonomic sagas to their compensation methods.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SagaStep {
    String name();
    String compensationMethod(); // Method name in the same bean to invoke for rolling back the step

    int maxRetries() default 0; // Number of retries on failure (0 means execute only once)
    long backoffMs() default 1000; // Wait time between retries in milliseconds
    Class<? extends Throwable>[] retryOn() default {Exception.class}; // Types of exceptions to retry on
}
