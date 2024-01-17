package io.github.hephaestusmetrics.model.queryresults;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class RawQueryResultTest {
    @Test
    public void sanityTest() throws JsonProcessingException {
        //given
        String tag = "given_tag";
        String metric = "given_metric";
        String json = "{\"tag\":\"" + tag + "\",\"metric\":\"" + metric + "\"}";

        //when
        RawQueryResult rawQueryResult = new ObjectMapper()
                                            .readerFor(RawQueryResult.class)
                                            .readValue(json);

        //then
        assertEquals(tag, rawQueryResult.getTag());
        assertEquals(metric, rawQueryResult.getMetric());
    }
}
