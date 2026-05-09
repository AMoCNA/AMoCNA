package com.kubiki.themis.condition.impl;

import com.kubiki.themis.config.ThemisProperties;
import com.kubiki.themis.knowledge.GraphDBGateway;
import com.kubiki.themis.model.ActionData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SparqlConditionEvaluatorTest {
    @Mock
    private GraphDBGateway graphDBGateway;

    @Mock
    private ThemisProperties properties;

    @Mock
    private ThemisProperties.Ontology ontology;

    @InjectMocks
    private SparqlConditionEvaluator evaluator;

    @Test
    void shouldSupportStateBasedCondition() {
        when(properties.ontology()).thenReturn(ontology);
        when(ontology.moaNamespace()).thenReturn("http://moa#");
        assertTrue(evaluator.supports("http://moa#StateBasedCondition"));
    }

    @Test
    void shouldEvaluateConditionViaGateway() {
        ActionData.ConditionData condition = new ActionData.ConditionData("id", "type", "ASK { ?s ?p ?o }");
        when(graphDBGateway.executeConditionQuery("ASK { ?s ?p ?o }")).thenReturn(true);

        assertTrue(evaluator.evaluate(condition));
        verify(graphDBGateway).executeConditionQuery("ASK { ?s ?p ?o }");
    }
}
