package com.kubiki.themis.config;

import com.kubiki.common.config.AmocnaCommonProperties;
import com.kubiki.daedalus.context.GlobalTemplateContext;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DaedalusInitializer {
    private final GlobalTemplateContext ctx;
    private final AmocnaCommonProperties properties;

    @PostConstruct
    public void init() {
        String actionsPrefix = properties.ontology().actionsPrefix();
        ctx.set("SPARQL_PREFIXES", String.format("PREFIX %s: <%s>", actionsPrefix, properties.ontology().actionsNamespace()));
        ctx.set("PREFIX::ACTIONS_PREFIX", actionsPrefix);
    }
}
