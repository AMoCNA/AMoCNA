package com.kubiki.themis.condition.impl;

import com.kubiki.themis.config.ThemisProperties;
import com.kubiki.themis.knowledge.GraphDBGateway;
import com.kubiki.themis.model.ActionData;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SparqlConditionEvaluatorTest {

    private GraphDBGateway graphDBGateway;
    private ThemisProperties properties;
    private SparqlConditionEvaluator evaluator;

    @BeforeEach
    void setUp() {
        graphDBGateway = Mockito.mock(GraphDBGateway.class);
        properties = Mockito.mock(ThemisProperties.class);
        when(properties.ontology()).thenReturn(new ThemisProperties.Ontology("http://moa#"));
        evaluator = new SparqlConditionEvaluator(graphDBGateway, properties);
    }

    @Test
    void shouldSupportStateBasedCondition() {
        assertTrue(evaluator.supports(SimpleValueFactory.getInstance().createIRI("http://moa#StateBasedCondition")));
    }

    @Test
    void shouldExecuteSparqlCondition() {
        String policy = "ASK { ?s ?p ?o }";
        ActionData.ConditionData condition = new ActionData.ConditionData(
                SimpleValueFactory.getInstance().createIRI("http://example.org/moa#cond1"),
                SimpleValueFactory.getInstance().createIRI("http://moa#StateBasedCondition"),
                policy);

        when(graphDBGateway.executeConditionQuery(policy)).thenReturn(true);

        boolean result = evaluator.evaluate(condition);

        assertTrue(result);
        verify(graphDBGateway).executeConditionQuery(policy);
    }
}
