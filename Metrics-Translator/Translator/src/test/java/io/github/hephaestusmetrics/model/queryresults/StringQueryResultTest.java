package io.github.hephaestusmetrics.model.queryresults;

import io.github.hephaestusmetrics.model.ResultType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class StringQueryResultTest {
    @Test
    public void sanityTest() {
        //given
        String tag = "TAG";
        Double timestamp = 1659261600D;
        String stringValue = "125";

        //when
        StringQueryResult stringQueryResult = new StringQueryResult(tag, timestamp, stringValue);

        //then
        assertEquals(tag, stringQueryResult.getTag());
        assertEquals(tag, stringQueryResult.get().getQueryTag());
        assertEquals(timestamp, stringQueryResult.get().getTimestamp());
        assertEquals(stringValue, stringQueryResult.get().getValueString());
        assertEquals(Double.parseDouble(stringValue), stringQueryResult.get().getValue());
        assertEquals(ResultType.STRING, stringQueryResult.get().getResultType());
        assertEquals(0, stringQueryResult.get().getLabels().size());
    }
}
