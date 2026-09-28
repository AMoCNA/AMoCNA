package com.kubiki.daedalus.knowledge;

import org.eclipse.rdf4j.query.BooleanQuery;
import org.eclipse.rdf4j.query.Update;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SparqlClientTest {
    @Mock
    private Repository repository;
    @Mock
    private RepositoryConnection connection;
    @Mock
    private BooleanQuery booleanQuery;
    @Mock
    private Update update;

    private SparqlClient sparqlClient;

    @BeforeEach
    void setUp() throws Exception {
        when(repository.getConnection()).thenReturn(connection);
        sparqlClient = new SparqlClient(repository);
    }

    @Test
    void executeUpdateWithSuccessShouldNotUseAskQuery() throws Exception {
        when(connection.prepareUpdate(anyString())).thenReturn(update);

        boolean result = sparqlClient.executeUpdateWithSuccess("DELETE { ?s ?p ?o } WHERE { ?s ?p ?o }");

        assertTrue(result);
        verify(connection).prepareUpdate(anyString());
        verify(update).execute();
        verify(connection, never()).prepareBooleanQuery(anyString());
    }
}
