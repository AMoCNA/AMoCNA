package com.kubiki.daedalus.context;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GlobalTemplateContext {
    private final Map<String, String> globals = new ConcurrentHashMap<>();

    public void set(String key, String value) {
        globals.put(key, value);
    }

    public String get(String key) {
        return globals.get(key);
    }

    public Map<String, String> getAll() {
        return globals;
    }
}
