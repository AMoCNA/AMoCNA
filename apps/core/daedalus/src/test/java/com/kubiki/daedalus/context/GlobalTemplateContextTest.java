package com.kubiki.daedalus.context;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class GlobalTemplateContextTest {
    private final GlobalTemplateContext context = new GlobalTemplateContext();

    @Test
    public void testSetAndGet() {
        context.set("key1", "value1");
        assertEquals("value1", context.get("key1"));
    }

    @Test
    public void testGetNonExistent() {
        assertNull(context.get("missing"));
    }

    @Test
    public void testGetAll() {
        context.set("a", "1");
        context.set("b", "2");
        Map<String, String> all = context.getAll();
        assertEquals(2, all.size());
        assertEquals("1", all.get("a"));
        assertEquals("2", all.get("b"));
    }
}
