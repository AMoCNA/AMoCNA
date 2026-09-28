package com.kubiki.palamedes.config;

import com.kubiki.common.config.AmocnaCommonProperties;
import lombok.RequiredArgsConstructor;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.http.HTTPRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class GraphDBConfig {

    private final AmocnaCommonProperties properties;


    @Bean
    public Repository repository() {
        HTTPRepository repository = new HTTPRepository(properties.graphdb().url(), properties.graphdb().repositoryId());
        repository.init();
        return repository;
    }
}
