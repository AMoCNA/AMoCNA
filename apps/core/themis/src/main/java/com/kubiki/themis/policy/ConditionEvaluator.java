package com.kubiki.themis.policy;

import com.kubiki.common.config.AmocnaCommonProperties;
import org.eclipse.rdf4j.query.BooleanQuery;
import org.eclipse.rdf4j.query.QueryLanguage;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class ConditionEvaluator {
    private static final Logger log = LoggerFactory.getLogger(ConditionEvaluator.class);

    private final AmocnaCommonProperties properties;
    private final RestClient restClient;
    private final SparqlRepository sparqlRepository;
    private final Repository repository;

    public ConditionEvaluator(AmocnaCommonProperties properties,
                              RestClient.Builder restClientBuilder,
                              SparqlRepository sparqlRepository,
                              Repository repository) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
        this.sparqlRepository = sparqlRepository;
        this.repository = repository;
    }

    public boolean evaluatePreConditions(String actionId) {
        return evaluateConditions(actionId, "hasPreCondition", "pre");
    }

    public boolean evaluatePostConditions(String actionId) {
        return evaluateConditions(actionId, "hasPostCondition", "post");
    }

    private boolean evaluateConditions(String actionId, String property, String logPrefix) {
        log.info("Evaluating {}-conditions for action: {}", logPrefix, actionId);
        String actionIri = properties.ontology().actionsNamespace() + actionId;
        String propertyIri = properties.ontology().actionsNamespace() + property;

        List<org.eclipse.rdf4j.query.BindingSet> conditions = sparqlRepository.fetchConditions(actionIri, propertyIri);
        if (conditions.isEmpty()) {
            log.info("No {}-conditions found for action: {}", logPrefix, actionId);
            return true;
        }

        for (org.eclipse.rdf4j.query.BindingSet bs : conditions) {
            String conditionQuery = bs.getValue("condition").stringValue();
            boolean result;
            if (isPromQL(conditionQuery)) {
                result = evaluatePromQL(conditionQuery);
            } else {
                result = evaluateSparqlAsk(conditionQuery);
            }

            if (!result) {
                log.warn("{}-condition failed for action {}: {}", logPrefix, actionId, conditionQuery);
                return false;
            }
        }

        return true;
    }

    private boolean isPromQL(String query) {
        String upper = query.toUpperCase();
        return !upper.contains("ASK") && !upper.contains("SELECT") && !upper.contains("PREFIX");
    }

    private boolean evaluateSparqlAsk(String sparql) {
        try (RepositoryConnection conn = repository.getConnection()) {
            BooleanQuery query = conn.prepareBooleanQuery(QueryLanguage.SPARQL, sparql);
            return query.evaluate();
        } catch (Exception e) {
            log.error("Error evaluating SPARQL ASK: {}", sparql, e);
            return false;
        }
    }

    private boolean evaluatePromQL(String promql) {
        try {
            var response = restClient.get()
                    .uri(properties.prometheus().url() + "/api/v1/query?query={query}", promql)
                    .retrieve()
                    .body(PrometheusResponse.class);

            return response != null && response.data() != null && !response.data().result().isEmpty();
        } catch (Exception e) {
            log.error("Error evaluating PromQL: {}", promql, e);
            return false;
        }
    }

    private record PrometheusResponse(String status, PrometheusData data) {
    }

    private record PrometheusData(String resultType, List<Object> result) {
    }
}
