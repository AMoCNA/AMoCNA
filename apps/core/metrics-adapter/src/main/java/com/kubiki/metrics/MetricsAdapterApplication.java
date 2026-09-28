package com.kubiki.metrics;

import com.kubiki.common.config.AmocnaCommonProperties;
import com.kubiki.daedalus.annotation.EnableDaedalusRepositories;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.reactive.function.client.WebClient;

@EnableScheduling
@SpringBootApplication(scanBasePackages = {"com.kubiki.metrics", "com.kubiki.common"})
@EnableConfigurationProperties({AmocnaCommonProperties.class})
@EnableDaedalusRepositories(basePackages = "com.kubiki.metrics.graph")
public class MetricsAdapterApplication {
    static void main(String[] args) {
        SpringApplication.run(MetricsAdapterApplication.class, args);
    }

    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}
