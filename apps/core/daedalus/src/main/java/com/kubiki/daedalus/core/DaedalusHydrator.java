package com.kubiki.daedalus.core;

import java.util.Map;

public interface DaedalusHydrator {
    String hydrate(String template, Map<String, Object> data);

    <T> T hydrateAndMap(String template, Map<String, Object> data, Class<T> targetClass);
}
