package com.kubiki.daedalus.core;

import com.kubiki.daedalus.annotation.TemplateType;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.kubiki.daedalus.core.DaedalusConstants.EMPTY_STRING;

public class Formatter {
    private final Map<Class<?>, ValueFormatter<?>> typeRegistry;
    private final Map<TemplateType, ValueFormatter<?>> annotationRegistry;

    public Formatter(List<ValueFormatter<?>> formatters) {
        this.typeRegistry = formatters.stream()
                .filter(f -> f.getSupportedType() != null)
                .collect(Collectors.toMap(ValueFormatter::getSupportedType, f -> f, (f1, f2) -> f1));
        this.annotationRegistry = formatters.stream()
                .filter(f -> f.getAnnotationType() != null)
                .collect(Collectors.toMap(ValueFormatter::getAnnotationType, f -> f, (f1, f2) -> f1));
    }

    public String format(Object value, TemplateType explicitType) {
        if (value == null) return EMPTY_STRING;

        ValueFormatter formatter;
        if (explicitType != null) {
            formatter = annotationRegistry.get(explicitType);
        } else {
            formatter = typeRegistry.get(value.getClass());
            if (formatter == null) {
                for (var entry : typeRegistry.entrySet()) {
                    if (entry.getKey().isAssignableFrom(value.getClass())) {
                        formatter = entry.getValue();
                        break;
                    }
                }
            }
        }

        if (formatter != null) {
            return formatter.format(value);
        }

        return value.toString();
    }
}
