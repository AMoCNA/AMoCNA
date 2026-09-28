package com.kubiki.themis.config;

import com.kubiki.common.config.AmocnaCommonProperties;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.http.HTTPRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GraphDBConfig {

    @Bean(destroyMethod = "shutDown")
    public Repository repository(AmocnaCommonProperties properties) {
        HTTPRepository repository = new HTTPRepository(
                properties.graphdb().url(),
                properties.graphdb().repositoryId()
        );
        repository.init();
        return repository;
    }
}
