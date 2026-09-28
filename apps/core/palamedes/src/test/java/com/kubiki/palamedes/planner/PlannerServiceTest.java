package com.kubiki.palamedes.planner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kubiki.daedalus.context.GlobalTemplateContext;
import com.kubiki.daedalus.core.DefaultDaedalusHydrator;
import com.kubiki.daedalus.core.Formatter;
import com.kubiki.daedalus.core.TemplateParser;
import com.kubiki.daedalus.core.format.PlainFormatter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlannerServiceTest {
    private final PlannerService plannerService = new PlannerService(
            new DefaultDaedalusHydrator(
                    new TemplateParser(),
                    new Formatter(List.of(new PlainFormatter())),
                    new GlobalTemplateContext(),
                    new ObjectMapper()
            )
    );

    @Test
    void shouldHydrateUrl() {
        String template = "https://k8s/${resourceName}";
        Map<String, String> data = Map.of("resourceName", "my-pod");
        String result = plannerService.hydrate(template, data);
        assertEquals("https://k8s/my-pod", result);
    }

    @Test
    void shouldHandleNullData() {
        String template = "https://k8s/no-data";
        String result = plannerService.hydrate(template, null);
        assertEquals(template, result);
    }
}
