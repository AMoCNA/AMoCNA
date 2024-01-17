package io.github.hephaestusmetrics.model.queryresults;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The type Raw query result.
 */
public class RawQueryResult {

    private final String tag;
    private final String metric; // metric value as string, as returned by Prometheus

    /**
     * Instantiates a new Raw query result.
     *
     * @param tag    the tag
     * @param metric the metric
     */
    @JsonCreator
    public RawQueryResult(@JsonProperty("tag") String tag, @JsonProperty("metric") String metric) {
        this.tag = tag;
        this.metric = metric;
    }

    /**
     * Gets tag.
     *
     * @return the tag
     */
    public String getTag() {
        return tag;
    }

    /**
     * Gets metric.
     *
     * @return the metric
     */
    public String getMetric() {
        return metric;
    }

}

