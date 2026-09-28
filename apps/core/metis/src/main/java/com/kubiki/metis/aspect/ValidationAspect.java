package com.kubiki.metis.aspect;

import com.kubiki.common.logging.ValidateIri;
import com.kubiki.common.logging.ValidateSchema;
import com.kubiki.metis.grpc.EntityDiscoveredEvent;
import com.kubiki.metis.grpc.RelationshipAssertedEvent;
import com.kubiki.metis.grpc.SensorBatch;
import com.kubiki.metis.grpc.SensorEvent;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.URI;

@Aspect
@Component
public class ValidationAspect {

    private static final Logger log = LoggerFactory.getLogger(ValidationAspect.class);

    @Around("@annotation(validateSchema)")
    public Object validate(ProceedingJoinPoint joinPoint, ValidateSchema validateSchema) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object[] args = joinPoint.getArgs();
        Annotation[][] parameterAnnotations = method.getParameterAnnotations();

        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                Object arg = args[i];
                if (arg instanceof SensorBatch request) {
                    validateBatch(request, validateSchema.schemaNamespace(), validateSchema.failOnInvalid());
                } else if (arg instanceof String value && parameterAnnotations != null && i < parameterAnnotations.length) {
                    for (Annotation ann : parameterAnnotations[i]) {
                        if (ann instanceof ValidateIri) {
                            checkIri(value, validateSchema.schemaNamespace(), validateSchema.failOnInvalid());
                        }
                    }
                }
            }
        }

        return joinPoint.proceed();
    }

    private void validateBatch(SensorBatch batch, String namespace, boolean failOnInvalid) {
        for (SensorEvent event : batch.getEventsList()) {
            if (event.hasEntityDiscovered()) {
                EntityDiscoveredEvent discovered = event.getEntityDiscovered();
                checkIri(discovered.getResourceIri(), namespace, failOnInvalid);
                checkIri(discovered.getOntologyType(), namespace, failOnInvalid);
            } else if (event.hasRelationshipAsserted()) {
                RelationshipAssertedEvent asserted = event.getRelationshipAsserted();
                checkIri(asserted.getSubjectIri(), namespace, failOnInvalid);
                checkIri(asserted.getPredicate(), namespace, failOnInvalid);
                checkIri(asserted.getObjectIri(), namespace, failOnInvalid);
            }
        }
    }

    private void checkIri(String val, String namespace, boolean failOnInvalid) {
        if (!isValidIri(val, namespace)) {
            log.error("Validation failed: '{}' is not a valid IRI", val);
            if (failOnInvalid) {
                throw new IllegalArgumentException("Invalid IRI parameter: " + val);
            }
        }
    }

    private boolean isValidIri(String val, String namespace) {
        if (val == null || val.isBlank()) return false;
        try {
            URI uri = URI.create(val);
            return uri.isAbsolute();
        } catch (Exception e) {
            return false;
        }
    }
}
