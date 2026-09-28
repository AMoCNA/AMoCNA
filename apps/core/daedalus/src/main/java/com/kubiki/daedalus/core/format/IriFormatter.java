package com.kubiki.daedalus.core.format;

import com.kubiki.daedalus.annotation.TemplateType;
import com.kubiki.daedalus.core.ValueFormatter;

import static com.kubiki.daedalus.core.DaedalusConstants.IRI_BEGIN;
import static com.kubiki.daedalus.core.DaedalusConstants.IRI_END;

public class IriFormatter implements ValueFormatter<Object> {
    @Override
    public String format(Object value) {
        return value != null ? IRI_BEGIN + value + IRI_END : "";
    }

    @Override
    public Class<Object> getSupportedType() {
        return null;
    }

    @Override
    public TemplateType getAnnotationType() {
        return TemplateType.IRI;
    }
}
