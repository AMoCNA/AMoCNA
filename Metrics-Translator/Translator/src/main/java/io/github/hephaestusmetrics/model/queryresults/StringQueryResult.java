package io.github.hephaestusmetrics.model.queryresults;

import io.github.hephaestusmetrics.model.ResultType;
import io.github.hephaestusmetrics.model.metrics.Metric;

import java.util.HashMap;
import java.util.List;

/**
 * The type String query result.
 */
public class StringQueryResult extends AbstractQueryResult {

    private final Metric metric;

    /**
     * Instantiates a new String query result.
     *
     * @param tag         the tag
     * @param timestamp   the timestamp
     * @param valueString the value string
     */
    public StringQueryResult(String tag, double timestamp, String valueString) {
        super(ResultType.STRING, tag);
        this.metric = new Metric(tag, ResultType.STRING, new HashMap<>(), timestamp, valueString);
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
