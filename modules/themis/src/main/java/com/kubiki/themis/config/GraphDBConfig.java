package com.kubiki.themis.config;

import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.http.HTTPRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GraphDBConfig {

    @Bean(initMethod = "init", destroyMethod = "shutDown")
    public Repository repository(ThemisProperties properties) {
        return new HTTPRepository(
                properties.graphdb().url(),
                properties.graphdb().repositoryId()
        );
    }
}
