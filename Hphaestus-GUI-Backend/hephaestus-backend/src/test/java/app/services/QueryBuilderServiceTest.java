package app.services;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;


public class QueryBuilderServiceTest {
    private static final String KEY_1 = "FILTER_1";
    private static final String VALUE_1 = "VALUE_1";

    private static final String KEY_2 = "FILTER_2";
    private static final String VALUE_2 = "VALUE_2";

    @Test
    void noFiltersTest() {
        //given
        QueryBuilderService queryBuilderService = new QueryBuilderService();

        //when
        String query = queryBuilderService.filtersToQuery(null);

        //then
        assertNull(query);
    }

    @Test
    void emptyFiltersTest() {
        //given
        QueryBuilderService queryBuilderService = new QueryBuilderService();
        Map<String, String> emptyFilters = Collections.emptyMap();

        //when
        String query = queryBuilderService.filtersToQuery(emptyFilters);

        //then
        assertNull(query);
    }

    @Test
    void buildQueryFromFiltersTest() {
        //given
        QueryBuilderService queryBuilderService = new QueryBuilderService();
        Map<String, String> filters = new HashMap<>() {{
            put(KEY_1, VALUE_1);
            put(KEY_2, VALUE_2);
        }};

        //when
        String query = queryBuilderService.filtersToQuery(filters);

        //then
        assertNotNull(query);
        assertTrue(query.contains(KEY_1 + "=\"" + VALUE_1 + "\""));
        assertTrue(query.contains(KEY_2 + "=\"" + VALUE_2 + "\""));
    }
}
