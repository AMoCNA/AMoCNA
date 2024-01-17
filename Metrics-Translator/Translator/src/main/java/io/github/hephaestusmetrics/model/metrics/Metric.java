package io.github.hephaestusmetrics.model.metrics;

import io.github.hephaestusmetrics.model.ResultType;

import java.util.Map;

/**
 * The type Metric.
 */
public class Metric {

    /**
     * The Query tag.
     */
    public final String queryTag;
    /**
     * The Result type.
     */
    public final ResultType resultType;
    /**
     * The Labels.
     */
    public final Map<String, String> labels;
    /**
     * The Timestamp.
     */
    public final double timestamp;
    /**
     * The Value string.
     */
    public final String valueString;
    /**
     * The Value.
     */
    public final Double value;
    /**
     * The Name.
     */
    public final String name;

    private static final String NAME_PARAM = "__name__";

    /**
     * Instantiates a new Metric.
     *
     * @param queryTag    the query tag
     * @param resultType  the result type
     * @param labels      the labels
     * @param timestamp   the timestamp
     * @param valueString the value string
     */
    public Metric(String queryTag, ResultType resultType, Map<String, String> labels, double timestamp, String valueString) {
        this.queryTag = queryTag;
        this.resultType = resultType;
        this.labels = labels;
        this.timestamp = timestamp;
        this.valueString = valueString;
        this.value = parseOrNull(valueString);
        this.name = labels.getOrDefault(NAME_PARAM, null);

    }

    /**
     * Instantiates a new Metric.
     *
     * @param queryTag   the query tag
     * @param resultType the result type
     * @param labels     the labels
     * @param timestamp  the timestamp
     * @param value      the value
     */
    public Metric(String queryTag, ResultType resultType, Map<String, String> labels, double timestamp, Double value) {
        this.queryTag = queryTag;
        this.resultType = resultType;
        this.labels = labels;
        this.timestamp = timestamp;
        this.valueString = value.toString();
        this.value = value;
        this.name = labels.getOrDefault(NAME_PARAM, null);
    }

    private static Double parseOrNull(String s) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Gets query tag.
     *
     * @return the query tag
     */
    public String getQueryTag() {
        return queryTag;
    }

    /**
     * Gets result type.
     *
     * @return the result type
     */
    public ResultType getResultType() {
        return resultType;
    }

    /**
     * Gets labels.
     *
     * @return the labels
     */
    public Map<String, String> getLabels() {
        return labels;
    }

    /**
     * Gets timestamp.
     *
     * @return the timestamp
     */
    public double getTimestamp() {
        return timestamp;
    }

    /**
     * Gets value.
     *
     * @return the value
     */
    public double getValue() {
        return value;
    }

    /**
     * Gets value string.
     *
     * @return the value string
     */
    public String getValueString() {
        return valueString;
    }

    /**
     * Gets name.
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

}
