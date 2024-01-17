package io.github.hephaestusmetrics.query;

import io.github.hephaestusmetrics.model.QueryInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * The type Query builder.
 */
public class QueryBuilder {

    private final String[][] metrics;
    private final List<String> queries;

    /**
     * Instantiates a new Query builder.
     *
     * @param metrics the metrics
     */
    public QueryBuilder(String[][] metrics) {
        this.queries = new ArrayList<>();
        this.metrics = metrics;
    }

    /**
     * Build queries.
     */
    public void buildQueries() {
        for(String[] metric: this.metrics) {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            for(String labels: metric) {
                String[] parameter = labels.split(":", 2);
                sb.append(parameter[0])
                        .append("=\"")
                        .append(parameter[1].trim())
                        .append("\"")
                        .append(", ");

            }
            sb.setLength(sb.length() - 2);
            sb.append("}");
            this.queries.add(sb.toString());
        }
    }

    /**
     * Gets queries.
     *
     * @return the queries
     */
    public QueryInfo getQueries() {
        return new QueryInfo(this.queries);
    }

}
