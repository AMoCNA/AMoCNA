package io.github.hephaestusmetrics.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class ResultTypeTest {
    @Test
    public void numberOfEnumTypes() {
        //given
        ResultType[] resultTypes = ResultType.values();

        //when
        int numberOfResultTypes = resultTypes.length;

        //then
        assertEquals(4, numberOfResultTypes);
    }

    @Test
    public void enumTypes() {
        //given
        List<ResultType> resultTypes = Arrays.asList(ResultType.values());

        //when
        boolean scalarPresent = resultTypes.contains(ResultType.SCALAR);
        boolean stringPresent = resultTypes.contains(ResultType.STRING);
        boolean matrixPresent = resultTypes.contains(ResultType.MATRIX);
        boolean vectorPresent = resultTypes.contains(ResultType.VECTOR);

        //then
        assertTrue(scalarPresent);
        assertTrue(stringPresent);
        assertTrue(matrixPresent);
        assertTrue(vectorPresent);
    }

    @ParameterizedTest
    @MethodSource("resultTypeStringSource")
    public void fromStringTest(String resultTypeString, ResultType expectedType) {
        //when
        ResultType resultType = ResultType.fromString(resultTypeString);

        //then
        assertEquals(expectedType, resultType);
    }

    @Test
    public void fromStringShouldThrowExceptionTest() {
        //given
        String wrongResultTypeString = "WRONG_RESULT_TYPE_STRING";

        //then
        assertThrows(RuntimeException.class, () -> ResultType.fromString(wrongResultTypeString));
    }

    private static Stream<Arguments> resultTypeStringSource() {
        return Stream.of(
                Arguments.of("string", ResultType.STRING),
                Arguments.of("scalar", ResultType.SCALAR),
                Arguments.of("vector", ResultType.VECTOR),
                Arguments.of("matrix", ResultType.MATRIX)
        );
    }
}
