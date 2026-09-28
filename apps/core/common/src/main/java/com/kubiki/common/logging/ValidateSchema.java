package com.kubiki.common.logging;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to enforce namespace and validation checks on incoming parameter elements.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidateSchema {
    String schemaNamespace() default "http://www.semanticweb.org/szymo/ontologies/2026/2/CNEEOnt/";
    boolean failOnInvalid() default true;
}
