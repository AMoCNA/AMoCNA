package com.kubiki.daedalus.core;

import com.kubiki.daedalus.annotation.TemplateType;
import com.kubiki.daedalus.core.format.CollectionFormatter;
import com.kubiki.daedalus.core.format.IriFormatter;
import com.kubiki.daedalus.core.format.LiteralFormatter;
import com.kubiki.daedalus.core.format.PlainFormatter;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FormatterTest {
    private final Formatter formatter = new Formatter(Arrays.asList(
            new IriFormatter(),
            new LiteralFormatter(),
            new PlainFormatter(),
            new CollectionFormatter()
    ));

    @Test
    public void testCustomFormatter() {
        ValueFormatter<String> custom = new ValueFormatter<>() {
            @Override
            public String format(String value) {
                return "CUSTOM:" + value;
            }

            @Override
            public Class<String> getSupportedType() {
                return String.class;
            }

            @Override
            public TemplateType getAnnotationType() {
                return TemplateType.PLAIN;
            }
        };
        Formatter customFormatter = new Formatter(Collections.singletonList(custom));
        assertEquals("CUSTOM:test", customFormatter.format("test", TemplateType.PLAIN));
    }

    @Test
    public void testFormatLiteralString() {
        assertEquals("\"hello\"", formatter.format("hello", TemplateType.LITERAL));
    }

    @Test
    public void testFormatLiteralNumber() {
        assertEquals("123", formatter.format(123, TemplateType.LITERAL));
    }

    @Test
    public void testFormatIRI() {
        assertEquals("<http://example.org>", formatter.format("http://example.org", TemplateType.IRI));
    }

    @Test
    public void testFormatCollection() {
        List<String> items = Arrays.asList("a", "b", "c");
        assertEquals("\"a\", \"b\", \"c\"", formatter.format(items, TemplateType.COLLECTION));
    }

    @Test
    public void testFormatNull() {
        assertEquals("", formatter.format(null, TemplateType.LITERAL));
    }
}
