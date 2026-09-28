package com.kubiki.common.logging;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;

@Aspect
@Component
public class MdcAspect {

    @Around("@annotation(com.kubiki.common.logging.MdcContext)")
    public Object manageMdc(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object[] args = joinPoint.getArgs();
        Parameter[] parameters = method.getParameters();

        List<String> keysAdded = new ArrayList<>();

        if (args != null) {
            for (int i = 0; i < parameters.length; i++) {
                if (i >= args.length) break;
                MdcParam[] mdcParams = parameters[i].getAnnotationsByType(MdcParam.class);
                if (mdcParams != null && mdcParams.length > 0) {
                    Object arg = args[i];
                    if (arg != null) {
                        for (MdcParam mdcParam : mdcParams) {
                            String mdcValue = extractValue(arg, mdcParam.property());
                            if (mdcValue != null) {
                                MDC.put(mdcParam.value(), mdcValue);
                                keysAdded.add(mdcParam.value());
                            }
                        }
                    }
                }
            }
        }

        try {
            return joinPoint.proceed();
        } finally {
            for (String key : keysAdded) {
                MDC.remove(key);
            }
        }
    }

    private String extractValue(Object obj, String property) {
        if (property == null || property.isBlank()) {
            return String.valueOf(obj);
        }

        try {
            Class<?> clazz = obj.getClass();
            
            // 1. Try method named directly after property (for Records or fluent getters)
            try {
                Method m = clazz.getMethod(property);
                Object res = m.invoke(obj);
                return res != null ? String.valueOf(res) : null;
            } catch (NoSuchMethodException e) {
                // 2. Try standard JavaBean getter format "getProperty"
                String getterName = "get" + property.substring(0, 1).toUpperCase() + property.substring(1);
                try {
                    Method m = clazz.getMethod(getterName);
                    Object res = m.invoke(obj);
                    return res != null ? String.valueOf(res) : null;
                } catch (NoSuchMethodException ex) {
                    // 3. Fallback to direct field access
                    try {
                        var field = clazz.getDeclaredField(property);
                        field.setAccessible(true);
                        Object res = field.get(obj);
                        return res != null ? String.valueOf(res) : null;
                    } catch (NoSuchFieldException nfe) {
                        return null;
                    }
                }
            }
        } catch (Exception e) {
            return null;
        }
    }
}
