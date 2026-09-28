package com.kubiki.palamedes.knowledge;

import com.kubiki.common.config.AmocnaCommonProperties;
import com.kubiki.palamedes.config.BeanConfig;
import com.kubiki.palamedes.config.DaedalusInitializer;
import com.kubiki.palamedes.config.PalamedesProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {BeanConfig.class, DaedalusInitializer.class})
@EnableConfigurationProperties({PalamedesProperties.class, AmocnaCommonProperties.class})
@ActiveProfiles("test")
class SparqlTestResourcesIT {

    @Autowired
    private TestSparqlRepository testSparqlRepository;

    @Test
    void shouldHydrateTemplateFromTestResources() {
        String actionId = "http://example.org/test-action";
        String hydratedQuery = testSparqlRepository.hydrateTestQuery(actionId);

        assertThat(hydratedQuery).contains("PREFIX");
        assertThat(hydratedQuery).contains("<" + actionId + ">");
        assertThat(hydratedQuery).contains("SELECT DISTINCT ?resource");
        assertThat(hydratedQuery).doesNotContain("${");
    }
}
