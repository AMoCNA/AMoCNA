package com.kubiki.daedalus.core.format;

import com.kubiki.daedalus.annotation.TemplateType;
import com.kubiki.daedalus.core.ValueFormatter;

import static com.kubiki.daedalus.core.DaedalusConstants.QUOTE;

public class LiteralFormatter implements ValueFormatter<Object> {
    @Override
    public String format(Object value) {
        if (value != null) {
            if (value instanceof Number || value instanceof Boolean) {
                return value.toString();
            }
            return QUOTE + escapeSparqlString(value.toString()) + QUOTE;
        }
        return "";
    }

    /** Escape characters that would break a SPARQL quoted string literal. */
    static String escapeSparqlString(String raw) {
        StringBuilder sb = new StringBuilder(raw.length() + 8);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    @Override
    public Class<Object> getSupportedType() {
        return null;
    }

    @Override
    public TemplateType getAnnotationType() {
        return TemplateType.LITERAL;
    }
}
