package com.kubiki.daedalus.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kubiki.daedalus.context.GlobalTemplateContext;
import com.kubiki.daedalus.exception.HydrationException;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class DefaultDaedalusHydrator implements DaedalusHydrator {
    private final TemplateParser parser;
    private final Formatter formatter;
    private final GlobalTemplateContext globalContext;
    private final ObjectMapper objectMapper;

    @Override
    public String hydrate(String template, Map<String, Object> data) {
        List<TemplateToken> tokens = parser.parse(template);
        Map<String, String> values = new HashMap<>(globalContext.getAll());

        if (data != null) {
            data.forEach((k, v) -> {
                values.put(k, formatter.format(v, null));
            });
        }

        StringBuilder sb = new StringBuilder();
        for (TemplateToken token : tokens) {
            if (token instanceof TemplateToken.StaticToken(String text)) {
                sb.append(text);
            } else if (token instanceof TemplateToken.VariableToken(String name, String defaultValue)) {
                String val = values.get(name);
                if (val == null) {
                    val = defaultValue;
                }
                if (val == null) {
                    throw new HydrationException("Missing variable: " + name);
                }
                sb.append(val);
            }
        }
        return sb.toString();
    }

    @Override
    @SneakyThrows
    public <T> T hydrateAndMap(String template, Map<String, Object> data, Class<T> targetClass) {
        String hydrated = hydrate(template, data);
        if (targetClass.equals(String.class)) {
            return (T) hydrated;
        }
        return objectMapper.readValue(hydrated, targetClass);
    }
}
