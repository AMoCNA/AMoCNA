package com.kubiki.metrics.config;

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
        String resourcePrefix = properties.ontology().resourcesPrefix();
        String resourcesNamespace = properties.ontology().resourcesNamespace();
        ctx.set("SPARQL_PREFIXES", String.format("PREFIX %s: <%s>", resourcePrefix, resourcesNamespace));
        ctx.set("PREFIX::CNEE_PREFIX", resourcePrefix);
    }
}
