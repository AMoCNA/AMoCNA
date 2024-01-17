package app.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SelectedCustomQueryTest {
    private static final String TAG = "TAG";
    private static final String QUERY_STRING = "QUERY_STRING";

    @Test
    void sanityTest() {
        //given
        SelectedCustomQuery selectedCustomQuery = new SelectedCustomQuery(TAG, QUERY_STRING);

        //when
        String tag = selectedCustomQuery.getTag();
        String queryString = selectedCustomQuery.getQueryString();

        //then
        assertEquals(TAG, tag);
        assertEquals(QUERY_STRING, queryString);
    }
}
