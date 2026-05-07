package com.kubiki.themis.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class PrometheusConfig {

    @Bean(name = "prometheusRestClient")
    public RestClient prometheusRestClient(ThemisProperties properties, RestClient.Builder builder) {
        return builder.baseUrl(properties.prometheus().url()).build();
    }
}
