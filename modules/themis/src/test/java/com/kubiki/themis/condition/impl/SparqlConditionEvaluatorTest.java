package com.kubiki.themis.condition.impl;

import com.kubiki.themis.knowledge.GraphDBGateway;
import com.kubiki.themis.model.ActionData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SparqlConditionEvaluatorTest {
    @Mock
    private GraphDBGateway graphDBGateway;

    @InjectMocks
    private SparqlConditionEvaluator evaluator;

    @Test
    void shouldSupportStateBasedCondition() {
        assertTrue(evaluator.supports("http://www.semanticweb.org/patryk/ontologies/2026/4/MoaMont#StateBasedCondition"));
    }

    @Test
    void shouldEvaluateConditionViaGateway() {
        ActionData.ConditionData condition = new ActionData.ConditionData("id", "type", "ASK { ?s ?p ?o }");
        when(graphDBGateway.executeConditionQuery("ASK { ?s ?p ?o }")).thenReturn(true);

        assertTrue(evaluator.evaluate(condition));
        verify(graphDBGateway).executeConditionQuery("ASK { ?s ?p ?o }");
    }
}
