package com.kubiki.themis.knowledge;

import com.kubiki.themis.config.ThemisProperties;
import com.kubiki.themis.constants.OntologyConstants;
import com.kubiki.themis.model.ActionData;
import com.kubiki.themis.model.ExecutionStatus;
import jakarta.annotation.PostConstruct;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.http.HTTPRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class GraphDBGateway {
    private static final Logger log = LoggerFactory.getLogger(GraphDBGateway.class);
    private final Repository repository;
    private final MoaMapper moaMapper;
    private final SparqlLoader sparqlLoader;
    private final String moamNamespace;

    public GraphDBGateway(ThemisProperties properties, MoaMapper moaMapper, SparqlLoader sparqlLoader) {
        this.repository = new HTTPRepository(properties.graphdb().url(), properties.graphdb().repositoryId());
        this.moaMapper = moaMapper;
        this.sparqlLoader = sparqlLoader;
        this.moamNamespace = properties.ontology().moamNamespace();
    }

    @PostConstruct
    public void init() {
        getRepository().init();
    }

    protected Repository getRepository() {
        return repository;
    }

    public void updateActionState(IRI actionId, ExecutionStatus state) {
        ValueFactory vf = getRepository().getValueFactory();
        IRI hasExecutionStatus = vf.createIRI(moamNamespace + OntologyConstants.PROP_HAS_EXECUTION_STATUS);
        Literal stateLiteral = vf.createLiteral(state.name());

        try (RepositoryConnection conn = getRepository().getConnection()) {
            conn.begin();
            // Remove existing status if any
            conn.remove(actionId, hasExecutionStatus, null);
            // Add new status
            conn.add(actionId, hasExecutionStatus, stateLiteral);

            conn.commit();
            log.info("Updated action {} state to {}", actionId, state);
        } catch (Exception e) {
            log.error("Failed to update action state: {}", e.getMessage());
            throw new RuntimeException("Persistence failure", e);
        }
    }

    public ActionData fetchActionStructure(IRI actionId) {
        String sparql = sparqlLoader.loadQuery("fetch-action-structure", Map.of("moamNamespace", moamNamespace));

        Map<IRI, List<BindingSet>> allBindings = new LinkedHashMap<>();
        try (RepositoryConnection conn = getRepository().getConnection()) {
            TupleQuery query = conn.prepareTupleQuery(sparql);
            try (TupleQueryResult result = query.evaluate()) {
                while (result.hasNext()) {
                    BindingSet bs = result.next();
                    IRI id = (IRI) bs.getValue("action");
                    allBindings.computeIfAbsent(id, k -> new ArrayList<>()).add(bs);
                }
            }
        } catch (Exception e) {
            log.error("Failed to fetch action structure", e);
            return null;
        }

        return moaMapper.mapAction(actionId, allBindings);
    }

    public List<ActionData.SimpleAction> findActionsForResource(IRI resourceIri) {
        String sparql = sparqlLoader.loadQuery("find-actions-for-resource", Map.of(
                "moamNamespace", moamNamespace,
                "resourceIri", resourceIri.stringValue()
        ));

        Map<IRI, List<BindingSet>> groups = new LinkedHashMap<>();
        try (RepositoryConnection conn = getRepository().getConnection()) {
            TupleQuery query = conn.prepareTupleQuery(sparql);
            try (TupleQueryResult result = query.evaluate()) {
                while (result.hasNext()) {
                    BindingSet bs = result.next();
                    IRI actionId = (IRI) bs.getValue("action");
                    groups.computeIfAbsent(actionId, k -> new ArrayList<>()).add(bs);
                }
            }
        } catch (Exception e) {
            log.error("Failed to query actions for resource {}: {}", resourceIri, e.getMessage());
        }

        List<ActionData.SimpleAction> actions = new ArrayList<>();
        for (List<BindingSet> group : groups.values()) {
            try {
                ActionData.SimpleAction action = moaMapper.mapSimpleActionGroup(group);
                if (action != null) {
                    actions.add(action);
                }
            } catch (Exception e) {
                log.warn("Skipping action mapping due to error: {}", e.getMessage());
            }
        }
        return actions;
    }

    public boolean executeConditionQuery(String query) {
        try (RepositoryConnection conn = getRepository().getConnection()) {
            return conn.prepareBooleanQuery(query).evaluate();
        } catch (Exception e) {
            log.error("Failed to execute condition query: {}", e.getMessage());
            return false;
        }
    }
}
