package com.kubiki.themis.knowledge;

import com.kubiki.themis.config.ThemisProperties;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GraphDBGatewayTest {

    private GraphDBGateway gateway;

    @Mock
    private Repository repository;

    @Mock
    private RepositoryConnection connection;

    @Mock
    private TupleQuery tupleQuery;

    @Mock
    private TupleQueryResult tupleQueryResult;

    @Mock
    private MoaMapper moaMapper;

    @BeforeEach
    void setUp() {
        ThemisProperties.Executors.Kubernetes kubernetes = new ThemisProperties.Executors.Kubernetes("http://localhost:8080", 5000);
        ThemisProperties.Executors executors = new ThemisProperties.Executors(kubernetes, new ThemisProperties.Executors.Logging("INFO"));
        ThemisProperties properties = new ThemisProperties(
                new ThemisProperties.GraphDB("http://localhost:7200", "themis"),
                executors
        );
        gateway = new GraphDBGateway(properties, moaMapper);
        // Inject mocked repository because the constructor creates a real HTTPRepository
        ReflectionTestUtils.setField(gateway, "repository", repository);
    }

    @Test
    void shouldFindActionsForResource() {
        // Given
        String resourceId = "worker-1";
        String actionUri = "http://www.semanticweb.org/patryk/ontologies/2026/4/MoaMont#ScaleUp";
        
        when(repository.getConnection()).thenReturn(connection);
        when(connection.prepareTupleQuery(anyString())).thenReturn(tupleQuery);
        when(tupleQuery.evaluate()).thenReturn(tupleQueryResult);
        when(tupleQueryResult.hasNext()).thenReturn(true, false);
        
        org.eclipse.rdf4j.query.BindingSet bindingSet = mock(org.eclipse.rdf4j.query.BindingSet.class);
        when(tupleQueryResult.next()).thenReturn(bindingSet);
        
        com.kubiki.themis.model.ActionData.SimpleAction mockAction = new com.kubiki.themis.model.ActionData.SimpleAction(
            actionUri, "ScaleUp", "worker-1", java.util.Map.of()
        );
        when(moaMapper.mapSimpleAction(bindingSet)).thenReturn(mockAction);

        // When
        java.util.List<com.kubiki.themis.model.ActionData> actions = gateway.findActionsForResource(resourceId);

        // Then
        assertEquals(1, actions.size());
        assertEquals(actionUri, actions.get(0).id());
        
        verify(connection).prepareTupleQuery(contains("targetsEntity <" + resourceId + ">"));
    }

    @Test
    void shouldInitializeRepository() {
        gateway.init();
        verify(repository).init();
    }

    @Test
    void shouldShutDownRepository() {
        gateway.shutDown();
        verify(repository).shutDown();
    }
}
