package com.kubiki.common.logging;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Aspect
@Component
public class ObserveSloAspect {

    private static final Logger log = LoggerFactory.getLogger(ObserveSloAspect.class);

    private final MeterRegistry registry;

    public ObserveSloAspect(@Autowired(required = false) MeterRegistry registry) {
        this.registry = registry;
    }

    @Around("@annotation(observeSlo)")
    public Object observe(ProceedingJoinPoint joinPoint, ObserveSLO observeSlo) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        long start = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            recordMetric(signature, start, observeSlo, "success");
            return result;
        } catch (Throwable t) {
            recordMetric(signature, start, observeSlo, "error");
            throw t;
        }
    }

    private void recordMetric(MethodSignature signature, long start, ObserveSLO observeSlo, String status) {
        long elapsed = System.currentTimeMillis() - start;
        if (elapsed > observeSlo.thresholdMs()) {
            log.warn("[SLO BREACH] Method '{}' took {}ms, exceeding the configured SLO threshold of {}ms!",
                    signature.getMethod().getName(), elapsed, observeSlo.thresholdMs());
        } else {
            log.debug("ObserveSLO: Method '{}' completed in {}ms (SLO: {}ms)",
                    signature.getMethod().getName(), elapsed, observeSlo.thresholdMs());
        }

        if (registry != null) {
            try {
                Timer.builder(observeSlo.name())
                        .description("SLO tracking timer")
                        .tag("method", signature.getMethod().getName())
                        .tag("status", status)
                        .tags(observeSlo.tags())
                        .register(registry)
                        .record(elapsed, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                // Ignore registry errors
            }
        }
    }
}
