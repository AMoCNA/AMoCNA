package io.github.hephaestusmetrics.model.queryresults;

import io.github.hephaestusmetrics.model.ResultType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class ScalarQueryResultTest {
    private static final String TAG = "TAG";
    private static final Double TIMESTAMP = 1659261600D;

    @Test
    public void sanityTestWithStringValue() {
        //given
        String stringValue = "125";

        //when
        ScalarQueryResult scalarQueryResult = new ScalarQueryResult(TAG, TIMESTAMP, stringValue);

        //then
        assertEquals(TAG, scalarQueryResult.getTag());
        assertEquals(TAG, scalarQueryResult.get().getQueryTag());
        assertEquals(TIMESTAMP, scalarQueryResult.get().getTimestamp());
        assertEquals(stringValue, scalarQueryResult.get().getValueString());
        assertEquals(Double.parseDouble(stringValue), scalarQueryResult.get().getValue());
        assertEquals(ResultType.SCALAR, scalarQueryResult.get().getResultType());
        assertEquals(0, scalarQueryResult.get().getLabels().size());
    }

    @Test
    public void sanityTestWithDoubleValue() {
        //given
        Double doubleValue = 125D;

        //when
        ScalarQueryResult scalarQueryResult = new ScalarQueryResult(TAG, TIMESTAMP, doubleValue);

        //then
        assertEquals(TAG, scalarQueryResult.getTag());
        assertEquals(TAG, scalarQueryResult.get().getQueryTag());
        assertEquals(TIMESTAMP, scalarQueryResult.get().getTimestamp());
        assertEquals(doubleValue.toString(), scalarQueryResult.get().getValueString());
        assertEquals(doubleValue, scalarQueryResult.get().getValue());
        assertEquals(ResultType.SCALAR, scalarQueryResult.get().getResultType());
        assertEquals(0, scalarQueryResult.get().getLabels().size());
    }
}
