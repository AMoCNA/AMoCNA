package com.kubiki.daedalus.knowledge;

import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

@Component
public class SparqlClient {
    private static final Logger log = LoggerFactory.getLogger(SparqlClient.class);
    private final Repository repository;

    public SparqlClient(Repository repository) {
        this.repository = repository;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public SparqlClient(ObjectProvider<Repository> repositoryProvider) {
        this.repository = repositoryProvider.getIfAvailable();
    }

    public <T> T executeQuery(String sparql, Function<Stream<BindingSet>, T> streamProcessor) {
        log.debug("Executing SPARQL:\n{}", sparql);
        try (RepositoryConnection connection = repository.getConnection()) {
            try (TupleQueryResult result = connection.prepareTupleQuery(sparql).evaluate()) {
                List<BindingSet> list = result.stream().toList();
                log.debug("Query returned {} rows", list.size());
                return streamProcessor.apply(list.stream());
            }
        }
    }

    public boolean executeUpdateWithSuccess(String sparql) {
        log.debug("Executing SPARQL UPDATE:\n{}", sparql);
        try (RepositoryConnection connection = repository.getConnection()) {
            connection.prepareUpdate(sparql).execute();
            return true;
        }
    }

    public boolean executeBooleanQuery(String sparql) {
        log.debug("Executing BOOLEAN SPARQL:\n{}", sparql);
        try (RepositoryConnection connection = repository.getConnection()) {
            return connection.prepareBooleanQuery(sparql).evaluate();
        }
    }

    public void executeWithConnection(Consumer<RepositoryConnection> action) {
        try (RepositoryConnection connection = repository.getConnection()) {
            action.accept(connection);
        }
    }

    public <T> T executeWithConnection(Function<RepositoryConnection, T> action) {
        try (RepositoryConnection connection = repository.getConnection()) {
            return action.apply(connection);
        }
    }
}
