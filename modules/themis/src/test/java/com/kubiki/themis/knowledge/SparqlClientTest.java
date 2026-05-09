package com.kubiki.themis.knowledge;

import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SparqlClientTest {

    @Mock
    private Repository repository;
    @Mock
    private RepositoryConnection connection;
    @Mock
    private TupleQuery tupleQuery;
    @Mock
    private TupleQueryResult queryResult;
    @Mock
    private BindingSet bindingSet;

    private SparqlClient sparqlClient;

    @BeforeEach
    void setUp() {
        sparqlClient = new SparqlClient(repository);
    }

    @Test
    void shouldExecuteQueryAndProcessStream() {
        when(repository.getConnection()).thenReturn(connection);
        when(connection.prepareTupleQuery(anyString())).thenReturn(tupleQuery);
        when(tupleQuery.evaluate()).thenReturn(queryResult);
        when(queryResult.stream()).thenReturn(Stream.of(bindingSet));

        List<BindingSet> result = sparqlClient.executeQuery("SELECT * WHERE { ?s ?p ?o }", 
            stream -> stream.toList());

        assertThat(result).hasSize(1);
        verify(connection).close();
        verify(queryResult).close();
    }
}
