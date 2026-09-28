package com.kubiki.metis.config;

import com.kubiki.daedalus.context.GlobalTemplateContext;
import com.kubiki.metis.sensor.IriFactory;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DaedalusInitializer {

    private final GlobalTemplateContext ctx;
    private final IriFactory iriFactory;

    @PostConstruct
    public void init() {
        log.info("Initializing Daedalus global variables for Metis...");

        String prefixes = String.format(
                """
                        PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                        PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                        PREFIX owl: <http://www.w3.org/2002/07/owl#>
                        PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
                        PREFIX cnee: <%s>
                        """,
                iriFactory.getCneeNamespace()
        );
        ctx.set("SPARQL_PREFIXES", prefixes);

        log.info("Finished initializing Daedalus global variables for Metis");
    }
}
