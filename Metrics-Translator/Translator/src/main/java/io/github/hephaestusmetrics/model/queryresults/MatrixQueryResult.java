package io.github.hephaestusmetrics.model.queryresults;

import io.github.hephaestusmetrics.model.ResultType;
import io.github.hephaestusmetrics.model.metrics.Metric;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The type Matrix query result.
 */
public class MatrixQueryResult extends AbstractQueryResult {

    private final List<List<Metric>> metrics;

    /**
     * Instantiates a new Matrix query result.
     *
     * @param tag     the tag
     * @param metrics the metrics
     */
    public MatrixQueryResult(String tag, List<List<Metric>> metrics) {
        super(ResultType.MATRIX, tag);
        this.metrics = metrics;
    }

    /**
     * Gets all.
     *
     * @return the all
     */
    public List<List<Metric>> getAll() {
        return metrics;
    }

    /**
     * Gets all flattened.
     *
     * @return the all flattened
     */
    public List<Metric> getAllFlattened() {
        return metrics.stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    /**
     * Get list.
     *
     * @param i the
     * @return the list
     */
    public List<Metric> get(int i) {
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
        return getAllFlattened();
    }

}
