package com.kubiki.daedalus.spring;

import com.kubiki.daedalus.context.GlobalTemplateContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = DaedalusAutoConfigDiscoveryTest.TestConfig.class)
class DaedalusAutoConfigDiscoveryTest {

    @Autowired(required = false)
    private TestRepo testRepo;

    @Autowired(required = false)
    private GlobalTemplateContext globalContext;

    @Test
    void shouldDiscoverAutoConfiguration() {
        assertThat(globalContext).isNotNull();
        assertThat(testRepo).isNotNull();

        globalContext.set("GLOBAL_VAR", "Discovered");
        String result = testRepo.greet("Discovery", "Test");
        assertThat(result).contains("Global: Discovered");
    }

    @Configuration
    @EnableAutoConfiguration
    static class TestConfig {
    }
}
