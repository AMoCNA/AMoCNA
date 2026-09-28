package com.kubiki.daedalus.core.format;

import com.kubiki.daedalus.annotation.TemplateType;
import com.kubiki.daedalus.core.ValueFormatter;

public class PlainFormatter implements ValueFormatter<Object> {
    @Override
    public String format(Object value) {
        return value != null ? value.toString() : "";
    }

    @Override
    public Class<Object> getSupportedType() {
        return (Class) String.class;
    }

    @Override
    public TemplateType getAnnotationType() {
        return TemplateType.PLAIN;
    }
}
