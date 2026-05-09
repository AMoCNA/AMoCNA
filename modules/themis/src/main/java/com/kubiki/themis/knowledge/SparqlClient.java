package com.kubiki.themis.knowledge;

import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.springframework.stereotype.Component;

import java.util.function.Function;
import java.util.stream.Stream;

@Component
public class SparqlClient {

    private final Repository repository;

    public SparqlClient(Repository repository) {
        this.repository = repository;
    }

    public <T> T executeQuery(String sparql, Function<Stream<BindingSet>, T> streamProcessor) {
        try (RepositoryConnection connection = repository.getConnection()) {
            try (TupleQueryResult result = connection.prepareTupleQuery(sparql).evaluate()) {
                return streamProcessor.apply(result.stream());
            }
        }
    }

    public void executeUpdate(String sparql) {
        try (RepositoryConnection connection = repository.getConnection()) {
            connection.begin();
            connection.prepareUpdate(sparql).execute();
            connection.commit();
        }
    }

    public boolean executeBooleanQuery(String sparql) {
        try (RepositoryConnection connection = repository.getConnection()) {
            return connection.prepareBooleanQuery(sparql).evaluate();
        }
    }

    public void executeWithConnection(java.util.function.Consumer<RepositoryConnection> action) {
        try (RepositoryConnection connection = repository.getConnection()) {
            action.accept(connection);
        }
    }
}

