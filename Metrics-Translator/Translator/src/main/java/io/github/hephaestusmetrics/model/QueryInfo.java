package io.github.hephaestusmetrics.model;

import java.util.List;

/**
 * The type Query info.
 */
public class QueryInfo {

    private List<String> queries;

    /**
     * Instantiates a new Query info.
     */
    public QueryInfo() {
    }

    /**
     * Instantiates a new Query info.
     *
     * @param queries the queries
     */
    public QueryInfo(List<String> queries) {
        this.queries = queries;
    }

    /**
     * Gets queries.
     *
     * @return the queries
     */
    public List<String> getQueries() {
        return queries;
    }

    /**
     * Sets queries.
     *
     * @param queries the queries
     */
    public void setQueries(List<String> queries) {
        this.queries = queries;
    }
}
