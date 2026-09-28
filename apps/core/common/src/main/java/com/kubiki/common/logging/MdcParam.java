package com.kubiki.common.logging;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to bind method parameter values or parameter object properties directly to MDC keys.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(MdcParamContainer.class)
public @interface MdcParam {
    /**
     * The MDC key name.
     */
    String value();

    /**
     * Optional property/method/record component name to extract from a complex parameter object.
     * If blank, maps the entire parameter directly via its String representation.
     */
    String property() default "";
}
