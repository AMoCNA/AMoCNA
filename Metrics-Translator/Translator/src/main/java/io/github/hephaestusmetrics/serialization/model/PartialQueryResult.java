package io.github.hephaestusmetrics.serialization.model;

import com.fasterxml.jackson.databind.node.ArrayNode;
import io.github.hephaestusmetrics.model.ResultType;
import io.github.hephaestusmetrics.model.metrics.Metric;
import io.github.hephaestusmetrics.model.queryresults.AbstractQueryResult;

import java.util.List;

/**
 * Partially parsed query result
 */
public class PartialQueryResult extends AbstractQueryResult {

    private final ArrayNode result;

    /**
     * Instantiates a new Partial query result.
     *
     * @param tag    the tag
     * @param type   the type
     * @param result the result
     */
    public PartialQueryResult(String tag, ResultType type, ArrayNode result) {
        super(type, tag);
        this.result = result;
    }

    /**
     * Gets result.
     *
     * @return the result
     */
    public ArrayNode getResult() {
        return result;
    }

    @Override
    public List<Metric> getMetrics() {
        return List.of();
    }

}
