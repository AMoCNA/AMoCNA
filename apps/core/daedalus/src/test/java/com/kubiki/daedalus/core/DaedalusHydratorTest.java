package com.kubiki.daedalus.core;

import com.kubiki.daedalus.context.GlobalTemplateContext;
import com.kubiki.daedalus.core.format.CollectionFormatter;
import com.kubiki.daedalus.core.format.IriFormatter;
import com.kubiki.daedalus.core.format.LiteralFormatter;
import com.kubiki.daedalus.core.format.PlainFormatter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DaedalusHydratorTest {

    private DaedalusHydrator hydrator;
    private GlobalTemplateContext globalContext;

    @BeforeEach
    void setUp() {
        globalContext = new GlobalTemplateContext();
        globalContext.set("GLOBAL", "GlobalVal");

        Formatter formatter = new Formatter(Arrays.asList(
                new IriFormatter(),
                new LiteralFormatter(),
                new PlainFormatter(),
                new CollectionFormatter()
        ));

        hydrator = new DefaultDaedalusHydrator(new TemplateParser(), formatter, globalContext, new com.fasterxml.jackson.databind.ObjectMapper());
    }

    @Test
    void shouldHydrateRawString() {
        String template = "Hello ${name}, global: ${GLOBAL}";
        Map<String, Object> data = Map.of("name", "Alice");

        String result = hydrator.hydrate(template, data);

        assertThat(result).isEqualTo("Hello Alice, global: GlobalVal");
    }

    @Test
    void shouldHydrateAndMapToPojo() {
        String template = "{\"name\": \"${name}\", \"age\": ${age}}";
        Map<String, Object> data = Map.of("name", "Bob", "age", 30);

        TestUser user = hydrator.hydrateAndMap(template, data, TestUser.class);

        assertThat(user.name()).isEqualTo("Bob");
        assertThat(user.age()).isEqualTo(30);
    }

    public record TestUser(String name, int age) {
    }
}
