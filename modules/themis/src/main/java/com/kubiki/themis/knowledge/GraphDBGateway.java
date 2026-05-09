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
    private final String moaNamespace;

    public GraphDBGateway(ThemisProperties properties, MoaMapper moaMapper) {
        this.repository = new HTTPRepository(properties.graphdb().url(), properties.graphdb().repositoryId());
        this.moaMapper = moaMapper;
        this.moaNamespace = properties.ontology().moaNamespace();
    }

    @PostConstruct
    public void init() {
        getRepository().init();
    }

    protected Repository getRepository() {
        return repository;
    }

    public void updateActionState(String actionId, ExecutionStatus state) {
        ValueFactory vf = getRepository().getValueFactory();
        IRI actionIri = vf.createIRI(actionId);
        IRI hasExecutionStatus = vf.createIRI(moaNamespace + OntologyConstants.PROP_HAS_EXECUTION_STATUS);
        Literal stateLiteral = vf.createLiteral(state.name());

        try (RepositoryConnection conn = getRepository().getConnection()) {
            conn.begin();
            // Remove existing status if any
            conn.remove(actionIri, hasExecutionStatus, null);
            // Add new status
            conn.add(actionIri, hasExecutionStatus, stateLiteral);

            conn.commit();
            log.info("Updated action {} state to {}", actionId, state);
        } catch (Exception e) {
            log.error("Failed to update action state: {}", e.getMessage());
            throw new RuntimeException("Persistence failure", e);
        }
    }

    public ActionData fetchActionStructure(String actionId) {
        String sparql = "PREFIX moa: <" + moaNamespace + "> " +
                "SELECT ?action ?intent ?target ?protocol ?instruction ?method ?payload " +
                "       ?preId ?preType ?prePolicy " +
                "       ?postId ?postType ?postPolicy " +
                "       ?step ?compensation WHERE { " +
                "  ?action a ?intent . " +
                "  OPTIONAL { ?action moa:" + OntologyConstants.PROP_TARGETS_ENTITY + " ?target } . " +
                "  OPTIONAL { ?action moa:" + OntologyConstants.PROP_EXECUTION_PROTOCOL + " ?protocol } . " +
                "  OPTIONAL { ?action moa:" + OntologyConstants.PROP_EXECUTION_INSTRUCTION + " ?instruction } . " +
                "  OPTIONAL { ?action moa:" + OntologyConstants.PROP_HTTP_METHOD + " ?method } . " +
                "  OPTIONAL { ?action moa:" + OntologyConstants.PROP_HTTP_PAYLOAD + " ?payload } . " +
                "  OPTIONAL { " +
                "    ?action moa:" + OntologyConstants.PROP_HAS_PRE_CONDITION + " ?preId . " +
                "    ?preId a ?preType . " +
                "    ?preId moa:" + OntologyConstants.PROP_POLICY_QUERY_STRING + " ?prePolicy . " +
                "  } . " +
                "  OPTIONAL { " +
                "    ?action moa:" + OntologyConstants.PROP_HAS_POST_CONDITION + " ?postId . " +
                "    ?postId a ?postType . " +
                "    ?postId moa:" + OntologyConstants.PROP_POLICY_QUERY_STRING + " ?postPolicy . " +
                "  } . " +
                "  OPTIONAL { " +
                "    ?action moa:" + OntologyConstants.PROP_IS_DECOMPOSED_INTO + " ?step . " +
                "    OPTIONAL { ?step moa:" + OntologyConstants.PROP_HAS_COMPENSATION + " ?compensation } . " +
                "  } . " +
                "  FILTER(?intent != moa:" + OntologyConstants.CLASS_AUTONOMIC_ACTION + " && ?intent != moa:" + OntologyConstants.CLASS_SIMPLE_ACTION + ") " +
                "}";

        Map<String, List<BindingSet>> allBindings = new LinkedHashMap<>();
        try (RepositoryConnection conn = getRepository().getConnection()) {
            TupleQuery query = conn.prepareTupleQuery(sparql);
            try (TupleQueryResult result = query.evaluate()) {
                while (result.hasNext()) {
                    BindingSet bs = result.next();
                    String id = bs.getValue("action").stringValue();
                    allBindings.computeIfAbsent(id, k -> new ArrayList<>()).add(bs);
                }
            }
        } catch (Exception e) {
            log.error("Failed to fetch action structure", e);
            return null;
        }

        return moaMapper.mapAction(actionId, allBindings);
    }

    public List<ActionData.SimpleAction> findActionsForResource(String resourceIri) {
        String sparql = "PREFIX moa: <" + moaNamespace + "> " +
                "SELECT ?action ?intent ?target ?protocol ?instruction ?method ?payload " +
                "       ?preId ?preType ?prePolicy " +
                "       ?postId ?postType ?postPolicy " +
                "WHERE { " +
                "  ?action moa:" + OntologyConstants.PROP_TARGETS_ENTITY + " <" + resourceIri + "> . " +
                "  ?action a ?intent . " +
                "  OPTIONAL { ?action moa:" + OntologyConstants.PROP_EXECUTION_PROTOCOL + " ?protocol } . " +
                "  OPTIONAL { ?action moa:" + OntologyConstants.PROP_EXECUTION_INSTRUCTION + " ?instruction } . " +
                "  OPTIONAL { ?action moa:" + OntologyConstants.PROP_HTTP_METHOD + " ?method } . " +
                "  OPTIONAL { ?action moa:" + OntologyConstants.PROP_HTTP_PAYLOAD + " ?payload } . " +
                "  OPTIONAL { " +
                "    ?action moa:" + OntologyConstants.PROP_HAS_PRE_CONDITION + " ?preId . " +
                "    ?preId a ?preType . " +
                "    ?preId moa:" + OntologyConstants.PROP_POLICY_QUERY_STRING + " ?prePolicy . " +
                "  } . " +
                "  OPTIONAL { " +
                "    ?action moa:" + OntologyConstants.PROP_HAS_POST_CONDITION + " ?postId . " +
                "    ?postId a ?postType . " +
                "    ?postId moa:" + OntologyConstants.PROP_POLICY_QUERY_STRING + " ?postPolicy . " +
                "  } . " +
                "  FILTER(?intent != moa:" + OntologyConstants.CLASS_AUTONOMIC_ACTION + " && ?intent != moa:" + OntologyConstants.CLASS_SIMPLE_ACTION + ") " +
                "}";

        Map<String, List<BindingSet>> groups = new LinkedHashMap<>();
        try (RepositoryConnection conn = getRepository().getConnection()) {
            TupleQuery query = conn.prepareTupleQuery(sparql);
            try (TupleQueryResult result = query.evaluate()) {
                while (result.hasNext()) {
                    BindingSet bs = result.next();
                    String actionId = bs.getValue("action").stringValue();
                    groups.computeIfAbsent(actionId, k -> new ArrayList<>()).add(bs);
                }
            }
        } catch (Exception e) {
            log.error("Failed to query actions for resource {}: {}", resourceIri, e.getMessage());
        }

        List<ActionData.SimpleAction> actions = new ArrayList<>();
        for (List<BindingSet> group : groups.values()) {
            ActionData.SimpleAction action = moaMapper.mapSimpleActionGroup(group);
            if (action != null) {
                actions.add(action);
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
