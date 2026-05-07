package com.kubiki.themis.knowledge;

import com.kubiki.themis.config.ThemisProperties;
import com.kubiki.themis.model.ActionData;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.http.HTTPRepository;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;

@Service
public class GraphDBGateway {
    private static final Logger log = LoggerFactory.getLogger(GraphDBGateway.class);
    private final Repository repository;
    private final MoaMapper moaMapper;
    private final String moaNamespace;

    public GraphDBGateway(ThemisProperties properties, MoaMapper moaMapper) {
        this.repository = new HTTPRepository(properties.graphdb().url(), properties.graphdb().repositoryId());
        this.moaMapper = moaMapper;
        this.moaNamespace = properties.ontology().moaNamespace();
    }

    @PostConstruct
    public void init() {
        this.repository.init();
    }

    public List<ActionData> findActionsForResource(String resourceId) {
        String sparql = "PREFIX moa: <" + moaNamespace + "> " +
                        "SELECT ?action ?intent ?target ?protocol ?instruction WHERE { " +
                        "  ?action moa:targetsEntity <" + resourceId + "> . " +
                        "  ?action a moa:AutonomicAction . " +
                        "  ?action a ?intent . " +
                        "  ?action moa:targetsEntity ?target . " +
                        "  OPTIONAL { ?action moa:executionProtocol ?protocol } . " +
                        "  OPTIONAL { ?action moa:executionInstruction ?instruction } . " +
                        "  FILTER(?intent != moa:AutonomicAction && ?intent != moa:SimpleAction) " +
                        "}";
        
        List<ActionData> actions = new ArrayList<>();
        try (RepositoryConnection conn = repository.getConnection()) {
            TupleQuery query = conn.prepareTupleQuery(sparql);
            try (TupleQueryResult result = query.evaluate()) {
                while (result.hasNext()) {
                    actions.add(moaMapper.mapSimpleAction(result.next()));
                }
            }
        } catch (Exception e) {
            log.error("CRITICAL: Failed to connect to GraphDB at {}. Is it running? Error: {}", repository, e.getMessage());
            throw new RuntimeException("Knowledge Base unavailable", e);
        }
        return actions;
    }

    @PreDestroy
    public void shutDown() {
        repository.shutDown();
    }
}
