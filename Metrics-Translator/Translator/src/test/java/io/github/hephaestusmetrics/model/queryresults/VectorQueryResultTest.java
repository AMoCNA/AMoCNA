package io.github.hephaestusmetrics.model.queryresults;

import io.github.hephaestusmetrics.model.ResultType;
import io.github.hephaestusmetrics.model.metrics.Metric;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class VectorQueryResultTest {
    private static final String TAG = "TAG";

    @Mock
    private Metric mockMetric;

    @Test
    public void sanityTest() {
        //given
        List<Metric> metrics = new ArrayList<>() {{
            add(mockMetric);
        }};

        //when
        VectorQueryResult vectorQueryResult = new VectorQueryResult(TAG, metrics);

        //then
        assertEquals(TAG, vectorQueryResult.getTag());
        assertEquals(ResultType.VECTOR, vectorQueryResult.getType());
        assertEquals(1, vectorQueryResult.getSize());
        assertEquals(metrics, vectorQueryResult.getAll());
        assertEquals(mockMetric, vectorQueryResult.get(0));
    }
}
