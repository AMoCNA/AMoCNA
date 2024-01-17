package io.github.hephaestusmetrics.model.queryresults;

import io.github.hephaestusmetrics.model.ResultType;
import io.github.hephaestusmetrics.model.metrics.Metric;

import java.util.List;

/**
 * Most generic class for metrics
 */
public abstract class AbstractQueryResult {

    private final ResultType type;
    private final String tag;

    /**
     * Instantiates a new Abstract query result.
     *
     * @param type the type
     * @param tag  the tag
     */
    protected AbstractQueryResult(ResultType type, String tag) {
        this.type = type;
        this.tag = tag;
    }

    /**
     * Gets metrics.
     *
     * @return the metrics
     */
    public abstract List<Metric> getMetrics();

    /**
     * Gets type.
     *
     * @return the type
     */
    public ResultType getType() {
        return type;
    }

    /**
     * Gets tag.
     *
     * @return the tag
     */
    public String getTag() {
        return tag;
    }
}
