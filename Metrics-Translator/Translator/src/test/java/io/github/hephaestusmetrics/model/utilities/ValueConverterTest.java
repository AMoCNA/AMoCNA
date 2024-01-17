package io.github.hephaestusmetrics.model.utilities;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ValueConverterTest {
    private final static double TIMESTAMP = 1659261600;
    private final static double VALUE = 45;

    private final static String UNPARSABLE = "12abc45";

    @Test
    public void normalConvertTest() {
        //given
        String[] values = {String.valueOf(TIMESTAMP), String.valueOf(VALUE)};

        //when
        double[] convertedValues = ValueConverter.convert(values);

        //then
        assertEquals(TIMESTAMP, convertedValues[0], 0.1);
        assertEquals(VALUE, convertedValues[1], 0.1);
    }

    @Test
    public void unparsableTimestampTest() {
        //given
        String[] values = {UNPARSABLE, String.valueOf(VALUE)};

        //when
        double[] convertedValues = ValueConverter.convert(values);

        //then
        assertEquals(Double.NaN, convertedValues[0], 0.1);
        assertEquals(VALUE, convertedValues[1], 0.1);
    }

    @Test
    public void unparsableValueTest() {
        //given
        String[] values = {String.valueOf(TIMESTAMP), UNPARSABLE};

        //when
        double[] convertedValues = ValueConverter.convert(values);

        //then
        assertEquals(TIMESTAMP, convertedValues[0], 0.1);
        assertEquals(Double.NaN, convertedValues[1], 0.1);
    }

    @Test
    public void unparsableTimestampAndValueTest() {
        //given
        String[] values = {UNPARSABLE, UNPARSABLE};

        //when
        double[] convertedValues = ValueConverter.convert(values);

        //then
        assertEquals(Double.NaN, convertedValues[0], 0.1);
        assertEquals(Double.NaN, convertedValues[1], 0.1);
    }

    @Test
    public void positiveInfinityValueTest() {
        //given
        String[] values = {String.valueOf(TIMESTAMP), "inf"};

        //when
        double[] convertedValues = ValueConverter.convert(values);

        //then
        assertEquals(TIMESTAMP, convertedValues[0], 0.1);
        assertEquals(Double.POSITIVE_INFINITY, convertedValues[1], 0.1);
    }

    @Test
    public void negativeInfinityValueTest() {
        //given
        String[] values = {String.valueOf(TIMESTAMP), "-inf"};

        //when
        double[] convertedValues = ValueConverter.convert(values);

        //then
        assertEquals(TIMESTAMP, convertedValues[0], 0.1);
        assertEquals(Double.NEGATIVE_INFINITY, convertedValues[1], 0.1);
    }

    @Test
    public void emptyValuesArrayTest() {
        //given
        String[] values = {};

        //when
        double[] convertedValues = ValueConverter.convert(values);

        //then
        assertEquals(Double.NaN, convertedValues[0], 0.1);
        assertEquals(Double.NaN, convertedValues[1], 0.1);
    }
}
