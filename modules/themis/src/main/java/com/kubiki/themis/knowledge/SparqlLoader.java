package com.kubiki.themis.knowledge;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class SparqlLoader {
    private final ResourceLoader resourceLoader;

    public SparqlLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public String loadRaw(String name) {
        Resource resource = resourceLoader.getResource("classpath:sparql/" + name + ".sparql");
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load SPARQL template: " + name, e);
        }
    }
}
