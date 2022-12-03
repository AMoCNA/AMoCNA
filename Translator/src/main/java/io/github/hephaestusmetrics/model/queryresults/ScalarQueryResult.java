package io.github.hephaestusmetrics.model.queryresults;

import io.github.hephaestusmetrics.model.ResultType;
import io.github.hephaestusmetrics.model.metrics.Metric;

import java.util.HashMap;
import java.util.List;

public class ScalarQueryResult extends AbstractQueryResult {

    private final Metric metric;

    public ScalarQueryResult(String tag, double timestamp, String valueString) {
        super(ResultType.SCALAR, tag);
        this.metric = new Metric(tag, ResultType.SCALAR, new HashMap<>(), timestamp, valueString);
    }

    public ScalarQueryResult(String tag, double timestamp, Double value) {
        super(ResultType.SCALAR, tag);
        this.metric = new Metric(tag, ResultType.SCALAR, new HashMap<>(), timestamp, value);
    }

    public Metric get() {
        return metric;
    }

    @Override
    public List<Metric> getMetrics() {
        return List.of(get());
    }
}
