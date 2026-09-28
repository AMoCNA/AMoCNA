package com.kubiki.metrics.prometheus;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads threshold definitions from metrics-adapter/src/main/resources/thresholds/*.yml
 */
@Component
@RequiredArgsConstructor
public class ThresholdsLoader {
    private static final Logger log = LoggerFactory.getLogger(ThresholdsLoader.class);
    private final ObjectMapper yamlMapper = createYamlMapper();
    private final List<ThresholdDefinition> loadedThresholds = new ArrayList<>();

    @PostConstruct
    public void loadThresholds() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources;
        try {
            resources = resolver.getResources("classpath:thresholds/*.yml");
        } catch (IOException e) {
            log.warn("Could not find thresholds directory on classpath: {}", e.getMessage());
            return;
        }

        yamlMapper.setDefaultPrettyPrinter(null);
        yamlMapper.setDefaultPropertyInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL);

        for (Resource resource : resources) {
            try {
                ThresholdDefinition threshold = yamlMapper.readValue(resource.getInputStream(), ThresholdDefinition.class);
                loadedThresholds.add(threshold);
                log.info("Loaded threshold definition: {} from {}", threshold.name(), resource.getFilename());
            } catch (Exception e) {
                log.error("Failed to load threshold from {}: {}", resource.getFilename(), e.getMessage());
            }
        }

        log.info("Total {} threshold(s) loaded", loadedThresholds.size());
    }

    public List<ThresholdDefinition> getThresholds() {
        return loadedThresholds;
    }

    private ObjectMapper createYamlMapper() {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.configure(DeserializationFeature.FAIL_ON_MISSING_EXTERNAL_TYPE_ID_PROPERTY, false);
        return mapper;
    }
}
