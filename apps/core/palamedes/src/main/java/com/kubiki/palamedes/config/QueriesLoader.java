package com.kubiki.palamedes.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Component
public class QueriesLoader {
    private static final Logger log = LoggerFactory.getLogger(QueriesLoader.class);
    private static final String QUERIES_PATH_PATTERN = "classpath:queries/*.yml";

    private final ObjectMapper yamlMapper = createYamlMapper();
    private final Map<String, QueryDefinition> queries = new HashMap<>();

    @PostConstruct
    public void loadQueries() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources;
        try {
            resources = resolver.getResources(QUERIES_PATH_PATTERN);
        } catch (IOException e) {
            log.warn("Could not find queries directory on classpath: {}", e.getMessage());
            return;
        }

        yamlMapper.setDefaultPrettyPrinter(null);
        yamlMapper.setDefaultPropertyInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL);

        for (Resource resource : resources) {
            try {
                QueryDefinition definition = yamlMapper.readValue(resource.getInputStream(), QueryDefinition.class);
                if (definition.name() == null || definition.name().isBlank()) {
                    log.warn("Query definition in {} has no name, skipping", resource.getFilename());
                    continue;
                }
                queries.put(definition.name(), definition);
                log.info("Loaded query definition: {} from {}", definition.name(), resource.getFilename());
            } catch (Exception e) {
                log.error("Failed to load query definition from {}: {}", resource.getFilename(), e.getMessage());
            }
        }

        log.info("Total {} query definition(s) loaded", queries.size());
    }

    public QueryDefinition getQuery(String name) {
        return queries.get(name);
    }

    private ObjectMapper createYamlMapper() {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.configure(DeserializationFeature.FAIL_ON_MISSING_EXTERNAL_TYPE_ID_PROPERTY, false);
        return mapper;
    }
}
