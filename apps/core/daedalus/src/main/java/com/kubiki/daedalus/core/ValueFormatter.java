package com.kubiki.daedalus.core;

import com.kubiki.daedalus.annotation.TemplateType;

public interface ValueFormatter<T> {
    String format(T value);

    Class<T> getSupportedType();

    TemplateType getAnnotationType();
}
