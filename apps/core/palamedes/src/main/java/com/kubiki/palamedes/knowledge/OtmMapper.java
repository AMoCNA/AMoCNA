package com.kubiki.palamedes.knowledge;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.query.BindingSet;
import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.Objects;

public class OtmMapper {

    private static final String RECORD_ID_FIELD = "id";

    public static <T extends Record> T map(List<BindingSet> bindings, Class<T> recordClass, IRI actionId, Object... manualArgs) {
        RecordComponent[] components = recordClass.getRecordComponents();
        Object[] args = new Object[components.length];

        for (int i = 0; i < components.length; i++) {
            RecordComponent comp = components[i];

            if (RECORD_ID_FIELD.equals(comp.getName())) {
                args[i] = actionId;
                continue;
            }

            RdfBinding anno = comp.getAnnotation(RdfBinding.class);
            if (anno == null) {
                args[i] = findManualArg(comp.getType(), manualArgs);
                continue;
            }

            String bindingName = anno.value();
            String defaultValue = anno.defaultValue();
            args[i] = extractValue(bindings, bindingName, comp.getType(), defaultValue);
        }

        try {
            Class<?>[] argTypes = new Class<?>[components.length];
            for (int i = 0; i < components.length; i++) {
                argTypes[i] = components[i].getType();
            }
            return recordClass.getDeclaredConstructor(argTypes).newInstance(args);
        } catch (Exception e) {
            throw new RuntimeException("OTM: Failed to instantiate record " + recordClass.getName(), e);
        }
    }

    private static Object findManualArg(Class<?> type, Object[] manualArgs) {
        if (manualArgs != null) {
            for (Object arg : manualArgs) {
                if (arg != null && type.isAssignableFrom(arg.getClass())) {
                    return arg;
                }
            }
        }
        if (type.equals(java.util.Map.class)) {
            return new java.util.HashMap<>();
        }
        if (type.equals(java.util.List.class)) {
            return new java.util.ArrayList<>();
        }
        return null;
    }

    private static Object extractValue(List<BindingSet> bindings, String name, Class<?> type, String defaultStr) {
        Value val = bindings.stream()
                .map(bs -> bs.getValue(name))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);

        if (val == null) {
            if (defaultStr == null || defaultStr.isEmpty()) {
                if (type.equals(Boolean.TYPE) || type.equals(Boolean.class)) return false;
                if (type.equals(Integer.TYPE) || type.equals(Integer.class)) return 0;
                if (type.equals(Float.TYPE) || type.equals(Float.class)) return 0.0f;
                return null;
            }
            return convertStringValue(defaultStr, type);
        }

        return convertRdfValue(val, type);
    }

    private static Object convertRdfValue(Value val, Class<?> type) {
        if (type.equals(IRI.class)) {
            return val instanceof IRI ? val : null;
        }
        if (type.equals(String.class)) {
            return val.stringValue();
        }
        if (type.equals(Boolean.TYPE) || type.equals(Boolean.class)) {
            if (val instanceof Literal) {
                return ((Literal) val).booleanValue();
            }
            return Boolean.parseBoolean(val.stringValue());
        }
        if (type.equals(Integer.TYPE) || type.equals(Integer.class)) {
            if (val instanceof Literal) {
                return ((Literal) val).intValue();
            }
            return Integer.parseInt(val.stringValue());
        }
        if (type.equals(Float.TYPE) || type.equals(Float.class)) {
            if (val instanceof Literal) {
                return ((Literal) val).floatValue();
            }
            return Float.parseFloat(val.stringValue());
        }
        if (type.equals(com.kubiki.common.model.Protocol.class)) {
            try {
                return com.kubiki.common.model.Protocol.valueOf(val.stringValue().toUpperCase());
            } catch (Exception e) {
                return null;
            }
        }
        if (type.equals(org.springframework.http.HttpMethod.class)) {
            try {
                return org.springframework.http.HttpMethod.valueOf(val.stringValue().toUpperCase());
            } catch (Exception e) {
                return org.springframework.http.HttpMethod.GET;
            }
        }

        return val;
    }

    private static Object convertStringValue(String val, Class<?> type) {
        if (type.equals(String.class)) {
            return val;
        }
        if (type.equals(Boolean.TYPE) || type.equals(Boolean.class)) {
            return Boolean.parseBoolean(val);
        }
        if (type.equals(Integer.TYPE) || type.equals(Integer.class)) {
            return Integer.parseInt(val);
        }
        if (type.equals(Float.TYPE) || type.equals(Float.class)) {
            return Float.parseFloat(val);
        }
        if (type.equals(com.kubiki.common.model.Protocol.class)) {
            try {
                return com.kubiki.common.model.Protocol.valueOf(val.toUpperCase());
            } catch (Exception e) {
                return null;
            }
        }
        if (type.equals(org.springframework.http.HttpMethod.class)) {
            try {
                return org.springframework.http.HttpMethod.valueOf(val.toUpperCase());
            } catch (Exception e) {
                return org.springframework.http.HttpMethod.GET;
            }
        }
        return null;
    }
}
