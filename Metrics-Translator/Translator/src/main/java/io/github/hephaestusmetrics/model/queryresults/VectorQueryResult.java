package io.github.hephaestusmetrics.model.queryresults;

import io.github.hephaestusmetrics.model.ResultType;
import io.github.hephaestusmetrics.model.metrics.Metric;

import java.util.List;

/**
 * The type Vector query result.
 */
public class VectorQueryResult extends AbstractQueryResult {

    private final List<Metric> metrics;

    /**
     * Instantiates a new Vector query result.
     *
     * @param tag     the tag
     * @param metrics the metrics
     */
    public VectorQueryResult(String tag, List<Metric> metrics) {
        super(ResultType.VECTOR, tag);
        this.metrics = metrics;
    }

    /**
     * Gets all.
     *
     * @return the all
     */
    public List<Metric> getAll() {
        return metrics;
    }

    /**
     * Get metric.
     *
     * @param i the
     * @return the metric
     */
    public Metric get(int i) {
        return metrics.get(i);
    }

    /**
     * Gets size.
     *
     * @return the size
     */
    public int getSize() {
        return metrics.size();
    }

    @Override
    public List<Metric> getMetrics() {
        return getAll();
    }

}
