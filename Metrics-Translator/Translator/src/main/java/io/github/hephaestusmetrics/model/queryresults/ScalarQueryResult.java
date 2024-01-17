package io.github.hephaestusmetrics.model.queryresults;

import io.github.hephaestusmetrics.model.ResultType;
import io.github.hephaestusmetrics.model.metrics.Metric;

import java.util.HashMap;
import java.util.List;

/**
 * The type Scalar query result.
 */
public class ScalarQueryResult extends AbstractQueryResult {

    private final Metric metric;

    /**
     * Instantiates a new Scalar query result.
     *
     * @param tag         the tag
     * @param timestamp   the timestamp
     * @param valueString the value string
     */
    public ScalarQueryResult(String tag, double timestamp, String valueString) {
        super(ResultType.SCALAR, tag);
        this.metric = new Metric(tag, ResultType.SCALAR, new HashMap<>(), timestamp, valueString);
    }

    /**
     * Instantiates a new Scalar query result.
     *
     * @param tag       the tag
     * @param timestamp the timestamp
     * @param value     the value
     */
    public ScalarQueryResult(String tag, double timestamp, Double value) {
        super(ResultType.SCALAR, tag);
        this.metric = new Metric(tag, ResultType.SCALAR, new HashMap<>(), timestamp, value);
    }

    /**
     * Get metric.
     *
     * @return the metric
     */
    public Metric get() {
        return metric;
    }

    @Override
    public List<Metric> getMetrics() {
        return List.of(get());
    }
}
