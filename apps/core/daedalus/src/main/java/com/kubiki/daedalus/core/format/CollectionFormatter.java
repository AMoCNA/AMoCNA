package com.kubiki.daedalus.core.format;

import com.kubiki.daedalus.annotation.TemplateType;
import com.kubiki.daedalus.core.ValueFormatter;

import java.util.Collection;
import java.util.stream.Collectors;

import static com.kubiki.daedalus.core.DaedalusConstants.COLLECTION_SEPARATOR;
import static com.kubiki.daedalus.core.DaedalusConstants.QUOTE;

public class CollectionFormatter implements ValueFormatter<Collection<?>> {
    @Override
    public String format(Collection<?> value) {
        return value.stream()
                .map(this::formatItem)
                .collect(Collectors.joining(COLLECTION_SEPARATOR));
    }

    private String formatItem(Object item) {
        if (item instanceof String) {
            return QUOTE + item + QUOTE;
        }
        return item != null ? item.toString() : "";
    }

    @Override
    public Class<Collection<?>> getSupportedType() {
        return (Class) Collection.class;
    }

    @Override
    public TemplateType getAnnotationType() {
        return TemplateType.COLLECTION;
    }
}
