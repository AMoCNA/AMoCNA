package io.github.hephaestusmetrics.serialization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.github.hephaestusmetrics.model.ResultType;
import io.github.hephaestusmetrics.model.queryresults.*;
import io.github.hephaestusmetrics.serialization.model.PartialQueryResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class TranslatorTest {
    private final Translator translator = new Translator();
    private final ObjectMapper mapper = new ObjectMapper();
    private static final String TAG = "GIVEN_TAG";
    private static ResultType RESULT_TYPE;
    private static String RESULT = "";
    private static String METRIC = "";
    private static String JSON_STRING = "";

    private static final String TIMESTAMP1 = "125";
    private static final String VALUE1 = "15";

    private static final String TIMESTAMP2 = "260";
    private static final String VALUE2 = "81";

    private static final String SCALAR_TYPE = "scalar";
    private static final String SCALAR_VALUE = "[" + TIMESTAMP1 + ", \"" + VALUE1 + "\"]";

    private static final String STRING_TYPE = "string";
    private static final String STRING_VALUE = "[" + TIMESTAMP1 + ", \"" + VALUE1 + "\"]";

    private static final String VECTOR_TYPE = "vector";
    private static final String VECTOR_VALUE = "[" + TIMESTAMP1 + ", \"" + VALUE1 + "\"]";

    private static final String MATRIX_TYPE = "matrix";
    private static final String MATRIX_VALUE = "[[" + TIMESTAMP1 + ", \"" + VALUE1 + "\"],[" + TIMESTAMP2 + ", \"" + VALUE2 + "\"]]";

    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @Nested
    class ParameterizedTranslatorTests {
        @ParameterizedTest
        @MethodSource("resultTypesSource")
        void partiallyDeserializeRawMetricStringTest(String resultType, String value) throws JsonProcessingException {
            //given
            prepareStrings(resultType, value);

            //when
            PartialQueryResult partialQueryResult = translator.partiallyDeserialize(JSON_STRING);

            //then
            assertPartialQueryResult(partialQueryResult);
        }

        @ParameterizedTest
        @MethodSource("resultTypesSource")
        void partiallyDeserializeRawQueryResultTest(String resultType, String value) throws JsonProcessingException {
            //given
            prepareStrings(resultType, value);
            RawQueryResult rawQueryResult = new RawQueryResult(TAG, METRIC);

            //when
            PartialQueryResult partialQueryResult = translator.partiallyDeserialize(rawQueryResult);

            //then
            assertPartialQueryResult(partialQueryResult);
        }

        @ParameterizedTest
        @MethodSource("resultTypesSource")
        void partiallyDeserializeTree(String resultType, String value) throws JsonProcessingException {
            //given
            prepareStrings(resultType, value);

            //when
            PartialQueryResult partialQueryResult = translator.partiallyDeserialize(mapper.readTree(JSON_STRING));

            //then
            assertPartialQueryResult(partialQueryResult);
        }

        @ParameterizedTest
        @MethodSource("resultTypesSource")
        void partiallyDeserializeTagAndTree(String resultType, String value) throws JsonProcessingException {
            //given
            prepareStrings(resultType, value);

            //when
            PartialQueryResult partialQueryResult = translator.partiallyDeserialize(TAG, mapper.readTree(METRIC));

            //then
            assertPartialQueryResult(partialQueryResult);
        }

        @ParameterizedTest
        @MethodSource("resultTypesSource")
        void parseResultRawMetricStringTest(String resultType, String value) {
            //given
            prepareStrings(resultType, value);

            //when
            AbstractQueryResult abstractQueryResult = translator.parseResult(JSON_STRING);

            //then
            assertAbstractQueryResult(abstractQueryResult);
        }

        @ParameterizedTest
        @MethodSource("resultTypesSource")
        void pareResultRawQueryResultTest(String resultType, String value) {
            //given
            prepareStrings(resultType, value);
            RawQueryResult rawQueryResult = new RawQueryResult(TAG, METRIC);

            //when
            AbstractQueryResult abstractQueryResult = translator.parseResult(rawQueryResult);

            //then
            assertAbstractQueryResult(abstractQueryResult);
        }

        @ParameterizedTest
        @MethodSource("resultTypesSource")
        void parseResultPartialQueryResultTest(String resultType, String value) throws JsonProcessingException {
            //given
            prepareStrings(resultType, value);
            PartialQueryResult partialQueryResult = new PartialQueryResult(TAG, RESULT_TYPE, (ArrayNode) mapper.readTree(RESULT));

            //when
            AbstractQueryResult abstractQueryResult = translator.parseResult(partialQueryResult);

            //then
            assertAbstractQueryResult(abstractQueryResult);
        }

        private void assertPartialQueryResult(PartialQueryResult partialQueryResult) throws JsonProcessingException {
            assertEquals(RESULT_TYPE, partialQueryResult.getType());
            assertEquals(TAG, partialQueryResult.getTag());
            assertEquals(mapper.readTree(RESULT), partialQueryResult.getResult());
        }

        private void assertAbstractQueryResult(AbstractQueryResult abstractQueryResult) {
            assertEquals(RESULT_TYPE, abstractQueryResult.getType());
            assertEquals(TAG, abstractQueryResult.getTag());
        }

        private Stream<Arguments> resultTypesSource() {
            return Stream.of(
                    Arguments.of(SCALAR_TYPE, SCALAR_VALUE),
                    Arguments.of(STRING_TYPE, STRING_VALUE),
                    Arguments.of(VECTOR_TYPE, VECTOR_VALUE),
                    Arguments.of(MATRIX_TYPE, MATRIX_VALUE)
            );
        }
    }

    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @Nested
    class ParseScalarResultTests {
        @BeforeAll
        void prepareNestedStrings() {
            prepareStrings(SCALAR_TYPE, SCALAR_VALUE);
        }

        @Test
        void parseScalarResultRawMetricStringTest() {
            //when
            ScalarQueryResult scalarQueryResult = translator.parseScalarResult(JSON_STRING);

            //then
            assertScalarQueryResult(scalarQueryResult);
        }

        @Test
        void parseScalarResultRawQueryResultTest() {
            //given
            RawQueryResult rawQueryResult = new RawQueryResult(TAG, METRIC);

            //when
            ScalarQueryResult scalarQueryResult = translator.parseScalarResult(rawQueryResult);

            //then
            assertScalarQueryResult(scalarQueryResult);
        }

        @Test
        void parseScalarResultPartialQueryResult() throws JsonProcessingException {
            //given
            PartialQueryResult partialQueryResult = new PartialQueryResult(TAG, RESULT_TYPE, (ArrayNode) mapper.readTree(RESULT));

            //when
            ScalarQueryResult scalarQueryResult = translator.parseScalarResult(partialQueryResult);

            //then
            assertScalarQueryResult(scalarQueryResult);
        }

        private void assertScalarQueryResult(ScalarQueryResult scalarQueryResult) {
            assertEquals(RESULT_TYPE, scalarQueryResult.getType());
            assertEquals(TAG, scalarQueryResult.getTag());
            assertEquals(RESULT_TYPE, scalarQueryResult.get().getResultType());
            assertEquals(TAG, scalarQueryResult.get().getQueryTag());
            assertEquals(0, scalarQueryResult.get().getLabels().size());
            assertEquals(Double.parseDouble(TIMESTAMP1), scalarQueryResult.get().getTimestamp());
            assertEquals(Double.parseDouble(VALUE1), scalarQueryResult.get().getValue());
            assertEquals(VALUE1, scalarQueryResult.get().getValueString());
        }
    }

    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @Nested
    class ParseStringResultTests {
        @BeforeAll
        void prepareNestedStrings() {
            prepareStrings(STRING_TYPE, STRING_VALUE);
        }

        @Test
        void parseStringResultRawMetricStringTest() {
            //when
            StringQueryResult stringQueryResult = translator.parseStringResult(JSON_STRING);

            //then
            assertStringQueryResult(stringQueryResult);
        }

        @Test
        void parseStringResultRawQueryResultTest() {
            //given
            RawQueryResult rawQueryResult = new RawQueryResult(TAG, METRIC);

            //when
            StringQueryResult stringQueryResult = translator.parseStringResult(rawQueryResult);

            //then
            assertStringQueryResult(stringQueryResult);
        }

        @Test
        void parseStringResultPartialQueryResult() throws JsonProcessingException {
            //given
            PartialQueryResult partialQueryResult = new PartialQueryResult(TAG, RESULT_TYPE, (ArrayNode) mapper.readTree(RESULT));

            //when
            StringQueryResult stringQueryResult = translator.parseStringResult(partialQueryResult);

            //then
            assertStringQueryResult(stringQueryResult);
        }

        private void assertStringQueryResult(StringQueryResult stringQueryResult) {
            assertEquals(RESULT_TYPE, stringQueryResult.getType());
            assertEquals(TAG, stringQueryResult.getTag());
            assertEquals(RESULT_TYPE, stringQueryResult.get().getResultType());
            assertEquals(TAG, stringQueryResult.get().getQueryTag());
            assertEquals(0, stringQueryResult.get().getLabels().size());
            assertEquals(Double.parseDouble(TIMESTAMP1), stringQueryResult.get().getTimestamp());
            assertEquals(Double.parseDouble(VALUE1), stringQueryResult.get().getValue());
            assertEquals(VALUE1, stringQueryResult.get().getValueString());
        }
    }

    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @Nested
    class ParseVectorResultTests {
        @BeforeAll
        void prepareNestedStrings() {
            prepareStrings(VECTOR_TYPE, VECTOR_VALUE);
        }

        @Test
        void parseVectorResultRawMetricStringTest() {
            //when
            VectorQueryResult vectorQueryResult = translator.parseVectorResult(JSON_STRING);

            //then
            assertVectorQueryResult(vectorQueryResult);
        }

        @Test
        void parseVectorResultRawQueryResultTest() {
            //given
            RawQueryResult rawQueryResult = new RawQueryResult(TAG, METRIC);

            //when
            VectorQueryResult vectorQueryResult = translator.parseVectorResult(rawQueryResult);

            //then
            assertVectorQueryResult(vectorQueryResult);
        }

        @Test
        void parseVectorResultPartialQueryResult() throws JsonProcessingException {
            //given
            PartialQueryResult partialQueryResult = new PartialQueryResult(TAG, RESULT_TYPE, (ArrayNode) mapper.readTree(RESULT));

            //when
            VectorQueryResult vectorQueryResult = translator.parseVectorResult(partialQueryResult);

            //then
            assertVectorQueryResult(vectorQueryResult);
        }

        private void assertVectorQueryResult(VectorQueryResult vectorQueryResult) {
            assertEquals(RESULT_TYPE, vectorQueryResult.getType());
            assertEquals(TAG, vectorQueryResult.getTag());
            assertEquals(RESULT_TYPE, vectorQueryResult.get(0).getResultType());
            assertEquals(TAG, vectorQueryResult.get(0).getQueryTag());
            assertTrue(vectorQueryResult.get(0).getLabels().isEmpty());
            assertEquals(1, vectorQueryResult.getSize());
            assertEquals(Double.parseDouble(TIMESTAMP1), vectorQueryResult.get(0).getTimestamp());
            assertEquals(Double.parseDouble(VALUE1), vectorQueryResult.get(0).getValue());
            assertEquals(VALUE1, vectorQueryResult.get(0).getValueString());
        }
    }

    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @Nested
    class ParseMatrixResultTests {
        @BeforeAll
        void prepareNestedStrings() {
            prepareStrings(MATRIX_TYPE, MATRIX_VALUE);
        }

        @Test
        void parseMatrixResultRawMetricStringTest() {
            //when
            MatrixQueryResult matrixQueryResult = translator.parseMatrixResult(JSON_STRING);

            //then
            assertMatrixQueryResult(matrixQueryResult);
        }

        @Test
        void parseMatrixResultRawQueryResultTest() {
            //given
            RawQueryResult rawQueryResult = new RawQueryResult(TAG, METRIC);

            //when
            MatrixQueryResult matrixQueryResult = translator.parseMatrixResult(rawQueryResult);

            //then
            assertMatrixQueryResult(matrixQueryResult);
        }

        @Test
        void parseMatrixResultPartialQueryResult() throws JsonProcessingException {
            //given
            PartialQueryResult partialQueryResult = new PartialQueryResult(TAG, RESULT_TYPE, (ArrayNode) mapper.readTree(RESULT));

            //when
            MatrixQueryResult matrixQueryResult = translator.parseMatrixResult(partialQueryResult);

            //then
            assertMatrixQueryResult(matrixQueryResult);
        }

        private void assertMatrixQueryResult(MatrixQueryResult matrixQueryResult) {
            assertEquals(RESULT_TYPE, matrixQueryResult.getType());
            assertEquals(TAG, matrixQueryResult.getTag());
            assertEquals(RESULT_TYPE, matrixQueryResult.get(0).get(0).getResultType());
            assertEquals(TAG, matrixQueryResult.get(0).get(0).getQueryTag());
            assertTrue(matrixQueryResult.get(0).get(0).getLabels().isEmpty());
            assertEquals(1, matrixQueryResult.getSize());
            assertEquals(Double.parseDouble(TIMESTAMP1), matrixQueryResult.get(0).get(0).getTimestamp());
            assertEquals(Double.parseDouble(VALUE1), matrixQueryResult.get(0).get(0).getValue());
            assertEquals(VALUE1, matrixQueryResult.get(0).get(0).getValueString());
            assertEquals(Double.parseDouble(TIMESTAMP2), matrixQueryResult.get(0).get(1).getTimestamp());
            assertEquals(Double.parseDouble(VALUE2), matrixQueryResult.get(0).get(1).getValue());
            assertEquals(VALUE2, matrixQueryResult.get(0).get(1).getValueString());
        }
    }

    private void prepareStrings(String resultType, String value) {
        RESULT_TYPE = ResultType.fromString(resultType);
        if (RESULT_TYPE == ResultType.SCALAR || RESULT_TYPE == ResultType.STRING) {
            RESULT = value;
        } else if (RESULT_TYPE == ResultType.VECTOR) {
            RESULT = "[{\"metric\":{}, \"value\":" + value + "}]";
        } else if (RESULT_TYPE == ResultType.MATRIX) {
            RESULT = "[{\"metric\":{}, \"values\":" + value + "}]";
        }
        METRIC = "{\"data\":{\"resultType\":\"" + resultType + "\",\"result\":" + RESULT + "}}";
        JSON_STRING = "{\"tag\":\"" + TAG + "\",\"metric\":" + METRIC + "}";
    }
}
