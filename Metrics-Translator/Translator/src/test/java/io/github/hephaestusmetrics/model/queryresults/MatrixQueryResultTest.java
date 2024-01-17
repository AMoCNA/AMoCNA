package io.github.hephaestusmetrics.model.queryresults;

import io.github.hephaestusmetrics.model.ResultType;
import io.github.hephaestusmetrics.model.metrics.Metric;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class MatrixQueryResultTest {
    private static final String TAG = "TAG";

    @Mock
    private Metric mockMetric1;

    @Mock
    private Metric mockMetric2;

    @Test
    public void sanityTest() {
        //given
        List<Metric> metrics1 = new ArrayList<>(){{
            add(mockMetric1);
        }};

        List<Metric> metrics2 = new ArrayList<>(){{
            add(mockMetric2);
        }};
        List<List<Metric>> metrics = new ArrayList<>(){{
            add(metrics1);
            add(metrics2);
        }};
        List<Metric> metricsFlattened = new ArrayList<>(){{
            add(mockMetric1);
            add(mockMetric2);
        }};

        //when
        MatrixQueryResult matrixQueryResult = new MatrixQueryResult(TAG, metrics);

        //then
        assertEquals(TAG, matrixQueryResult.getTag());
        assertEquals(ResultType.MATRIX, matrixQueryResult.getType());
        assertEquals(2, matrixQueryResult.getSize());
        assertEquals(metrics, matrixQueryResult.getAll());
        assertEquals(metricsFlattened, matrixQueryResult.getAllFlattened());
        assertEquals(metrics1, matrixQueryResult.get(0));
        assertEquals(metrics2, matrixQueryResult.get(1));
    }
}
