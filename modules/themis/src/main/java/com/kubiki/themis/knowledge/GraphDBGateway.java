package com.kubiki.themis.knowledge;

import com.kubiki.themis.constants.OntologyConstants;
import com.kubiki.themis.model.ActionData;
import com.kubiki.themis.model.ExecutionStatus;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.query.BindingSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class GraphDBGateway {
    private static final Logger log = LoggerFactory.getLogger(GraphDBGateway.class);
    
    private final SparqlClient sparqlClient;
    private final SparqlQueryBuilder sparqlQueryBuilder;
    private final ModelMapper modelMapper;
    private final OntologyRegistry ontologyRegistry;

    public GraphDBGateway(SparqlClient sparqlClient, 
                          SparqlQueryBuilder sparqlQueryBuilder,
                          ModelMapper modelMapper,
                          OntologyRegistry ontologyRegistry) {
        this.sparqlClient = sparqlClient;
        this.sparqlQueryBuilder = sparqlQueryBuilder;
        this.modelMapper = modelMapper;
        this.ontologyRegistry = ontologyRegistry;
    }

    public void updateActionState(IRI actionId, ExecutionStatus state) {
        IRI hasExecutionStatus = ontologyRegistry.moam(OntologyConstants.PROP_HAS_EXECUTION_STATUS);
        
        sparqlClient.executeWithConnection(conn -> {
            ValueFactory vf = conn.getValueFactory();
            Literal stateLiteral = vf.createLiteral(state.name());
            
            conn.begin();
            conn.remove(actionId, hasExecutionStatus, null);
            conn.add(actionId, hasExecutionStatus, stateLiteral);
            conn.commit();
            log.info("Updated action {} state to {}", actionId, state);
        });
    }

    public ActionData fetchActionStructure(IRI actionId) {
        String sparql = sparqlQueryBuilder.builder()
                .template("fetch-action-structure")
                .build();

        return sparqlClient.executeQuery(sparql, stream -> {
            Map<IRI, List<BindingSet>> allBindings = stream.collect(
                    Collectors.groupingBy(bs -> (IRI) bs.getValue("action"), LinkedHashMap::new, Collectors.toList())
            );
            Result<ActionData> result = modelMapper.mapAction(actionId, allBindings);
            if (result.isSuccess()) {
                return result.value();
            } else {
                log.error("Failed to map action structure for {}: {}", actionId, result.error());
                return null;
            }
        });
    }

    public List<ActionData.SimpleAction> findActionsForResource(IRI resourceIri) {
        String sparql = sparqlQueryBuilder.builder()
                .template("find-actions-for-resource")
                .variable("resourceIri", resourceIri)
                .build();

        return sparqlClient.executeQuery(sparql, stream -> stream
                .collect(Collectors.groupingBy(bs -> (IRI) bs.getValue("action")))
                .entrySet().stream()
                .map(entry -> modelMapper.mapAction(entry.getKey(), Map.of(entry.getKey(), entry.getValue())))
                .filter(Result::isSuccess)
                .map(Result::value)
                .filter(ad -> ad instanceof ActionData.SimpleAction)
                .map(ad -> (ActionData.SimpleAction) ad)
                .toList()
        );
    }

    public boolean executeConditionQuery(String query) {
        return sparqlClient.executeBooleanQuery(query);
    }
}
