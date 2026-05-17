package com.kubiki.palamedes.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kubiki.daedalus.annotation.EnableDaedalusRepositories;
import com.kubiki.daedalus.context.GlobalTemplateContext;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import static com.kubiki.palamedes.knowledge.KnowledgeConstants.*;

@Configuration
@EnableDaedalusRepositories(basePackages = "com.kubiki.palamedes.knowledge")
public class BeanConfig {

    @Bean
    @Primary
    public ObjectMapper getObjectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public CommandLineRunner initDaedalus(GlobalTemplateContext ctx, PalamedesProperties properties) {
        return args -> {
            String prefixes = String.format(
                    """
                            PREFIX %s: <%s>
                            PREFIX %s: <%s>
                            PREFIX %s: <%s>
                            PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                            PREFIX owl: <http://www.w3.org/2002/07/owl#>
                            """,
                    properties.ontology().actionsPrefix(), properties.ontology().actionsNamespace(),
                    properties.ontology().resourcesPrefix(), properties.ontology().resourcesNamespace(),
                    properties.ontology().bridgePrefix(), properties.ontology().bridgeNamespace()
            );
            ctx.set("SPARQL_PREFIXES", prefixes);

            ctx.set("PREFIX::" + ACTIONS_PREFIX_VARIABLE, properties.ontology().actionsPrefix());
            ctx.set("PREFIX::" + RESOURCES_PREFIX_VARIABLE, properties.ontology().resourcesPrefix());
            ctx.set("PREFIX::" + BRIDGE_PREFIX_VARIABLE, properties.ontology().bridgePrefix());

            ctx.set("INDIVIDUAL::" + STATE_INITIAL, properties.ontology().states().getOrDefault(PROPERTIES_INITIAL_STATE_NAME, DEFAULT_STATE_INITIAL));
            ctx.set("INDIVIDUAL::" + STATE_PLANNED, properties.ontology().states().getOrDefault(PROPERTIES_PLANNED_STATE_NAME, DEFAULT_STATE_PLANNED));
            ctx.set("INDIVIDUAL::" + STATE_VALIDATED, properties.ontology().states().getOrDefault(PROPERTIES_VALIDATED_STATE_NAME, DEFAULT_STATE_VALIDATED));
            ctx.set("INDIVIDUAL::" + STATE_IN_PROGRESS, properties.ontology().states().getOrDefault(PROPERTIES_IN_PROGRESS_STATE_NAME, DEFAULT_STATE_IN_PROGRESS));
            ctx.set("INDIVIDUAL::" + STATE_SUCCEEDED, properties.ontology().states().getOrDefault(PROPERTIES_SUCCEEDED_STATE_NAME, DEFAULT_STATE_SUCCEEDED));
            ctx.set("INDIVIDUAL::" + STATE_FAILED, properties.ontology().states().getOrDefault(PROPERTIES_FAILED_STATE_NAME, DEFAULT_STATE_FAILED));
            ctx.set("INDIVIDUAL::" + STATE_COMPENSATING, properties.ontology().states().getOrDefault(PROPERTIES_COMPENSATING_STATE_NAME, DEFAULT_STATE_COMPENSATING));
        };
    }
}
