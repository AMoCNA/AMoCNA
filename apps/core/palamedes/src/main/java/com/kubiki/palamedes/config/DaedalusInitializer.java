package com.kubiki.palamedes.config;

import com.kubiki.common.config.AmocnaCommonProperties;
import com.kubiki.daedalus.context.GlobalTemplateContext;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import static com.kubiki.palamedes.knowledge.KnowledgeConstants.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class DaedalusInitializer {

    private final GlobalTemplateContext ctx;
    private final AmocnaCommonProperties commonProperties;
    private final PalamedesProperties properties;

    @PostConstruct
    public void init() {
        log.info("Initializing Daedalus global variables...");

        String prefixes = String.format(
                """
                        PREFIX %s: <%s>
                        PREFIX %s: <%s>
                        PREFIX %s: <%s>
                        PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                        PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                        PREFIX owl: <http://www.w3.org/2002/07/owl#>
                        """,
                commonProperties.ontology().actionsPrefix(), commonProperties.ontology().actionsNamespace(),
                commonProperties.ontology().resourcesPrefix(), commonProperties.ontology().resourcesNamespace(),
                commonProperties.ontology().bridgePrefix(), commonProperties.ontology().bridgeNamespace()
        );
        ctx.set("SPARQL_PREFIXES", prefixes);

        ctx.set("PREFIX::" + ACTIONS_PREFIX_VARIABLE, commonProperties.ontology().actionsPrefix());
        ctx.set("PREFIX::" + RESOURCES_PREFIX_VARIABLE, commonProperties.ontology().resourcesPrefix());
        ctx.set("PREFIX::" + BRIDGE_PREFIX_VARIABLE, commonProperties.ontology().bridgePrefix());

        ctx.set("INDIVIDUAL::" + STATE_INITIAL, properties.states().actionStates().getOrDefault(PROPERTIES_INITIAL_STATE_NAME, DEFAULT_STATE_INITIAL));
        ctx.set("INDIVIDUAL::" + STATE_PLANNED, properties.states().actionStates().getOrDefault(PROPERTIES_PLANNED_STATE_NAME, DEFAULT_STATE_PLANNED));
        ctx.set("INDIVIDUAL::" + STATE_VALIDATED, properties.states().actionStates().getOrDefault(PROPERTIES_VALIDATED_STATE_NAME, DEFAULT_STATE_VALIDATED));
        ctx.set("INDIVIDUAL::" + STATE_IN_PROGRESS, properties.states().actionStates().getOrDefault(PROPERTIES_IN_PROGRESS_STATE_NAME, DEFAULT_STATE_IN_PROGRESS));
        ctx.set("INDIVIDUAL::" + STATE_SUCCEEDED, properties.states().actionStates().getOrDefault(PROPERTIES_SUCCEEDED_STATE_NAME, DEFAULT_STATE_SUCCEEDED));
        ctx.set("INDIVIDUAL::" + STATE_FAILED, properties.states().actionStates().getOrDefault(PROPERTIES_FAILED_STATE_NAME, DEFAULT_STATE_FAILED));
        ctx.set("INDIVIDUAL::" + STATE_COMPENSATING, properties.states().actionStates().getOrDefault(PROPERTIES_COMPENSATING_STATE_NAME, DEFAULT_STATE_COMPENSATING));

        log.info("Finished initializing Daedalus global variables");
    }
}
