package io.github.hephaestusmetrics.model.metrics;

import io.github.hephaestusmetrics.model.ResultType;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class MetricTest {
    private static final String QUERY_TAG = "QUERY_TAG";
    private static final ResultType RESULT_TYPE = ResultType.STRING;
    private static final Map<String, String> LABELS = new HashMap<>(){{
        put("LABEL1", "VALUE1");
    }};
    private static final Double TIMESTAMP = 1659261600D;

    @Test
    public void sanityTestWithDoubleValue() {
        //given
        Double doubleValue = 125D;

        //then
        Metric metric = new Metric(QUERY_TAG, RESULT_TYPE, LABELS, TIMESTAMP, doubleValue);

        //then
        assertEquals(QUERY_TAG, metric.getQueryTag());
        assertEquals(RESULT_TYPE, metric.getResultType());
        assertEquals(LABELS, metric.getLabels());
        assertEquals(TIMESTAMP, metric.getTimestamp());
        assertEquals(doubleValue.toString(), metric.getValueString());
        assertEquals(doubleValue, metric.getValue());
    }

    @Test
    public void sanityTestWithStringValue() {
        //given
        String stringValue = "125";

        //then
        Metric metric = new Metric(QUERY_TAG, RESULT_TYPE, LABELS, TIMESTAMP, stringValue);

        //then
        assertEquals(QUERY_TAG, metric.getQueryTag());
        assertEquals(RESULT_TYPE, metric.getResultType());
        assertEquals(LABELS, metric.getLabels());
        assertEquals(TIMESTAMP, metric.getTimestamp());
        assertEquals(stringValue, metric.getValueString());
        assertEquals(Double.parseDouble(stringValue), metric.getValue());
    }

    @Test
    public void unparsableStringValueTest() {
        //given
        String unparsableStringValue = "UNPARSABLE_STRING_VALUE";

        //then
        Metric metric = new Metric(QUERY_TAG, RESULT_TYPE, LABELS, TIMESTAMP, unparsableStringValue);

        //then
        assertEquals(QUERY_TAG, metric.getQueryTag());
        assertEquals(RESULT_TYPE, metric.getResultType());
        assertEquals(LABELS, metric.getLabels());
        assertEquals(TIMESTAMP, metric.getTimestamp());
        assertEquals(unparsableStringValue, metric.getValueString());
        assertNull(metric.value);
    }
}
