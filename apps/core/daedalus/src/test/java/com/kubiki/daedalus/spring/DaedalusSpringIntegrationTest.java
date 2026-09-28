package com.kubiki.daedalus.spring;

import com.kubiki.daedalus.context.GlobalTemplateContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = DaedalusSpringIntegrationTest.TestConfig.class)
class DaedalusSpringIntegrationTest {

    @Autowired
    private TestRepo testRepo;

    @Autowired
    private GlobalTemplateContext globalContext;

    @Test
    void shouldInjectAndUseProxy() {
        globalContext.set("GLOBAL_VAR", "SpringValue");

        String result = testRepo.greet("Spring", "World");

        assertThat(result).contains("Hello \"Spring\", welcome to \"World\"!");
        assertThat(result).contains("Global: SpringValue");
    }

    @Configuration
    @Import(DaedalusAutoConfiguration.class)
    static class TestConfig {
    }
}
