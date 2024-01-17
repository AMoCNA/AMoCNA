package app.providers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class QueryProviderTest {
    @Mock
    private RestTemplate restTemplate;

    private static final String ADDRESS = "http://127.0.0.1:12345";
    private static final String QUERY_SUFFIX = "/api/v1/query?query={my_query}";
    private static final String PROMETHEUS_VALUE = "Test Prometheus Value";
    private static final String PROMETHEUS_QUERY = "Test Prometheus Query";

    @Test
    void queryFiltersTest() {
        //when
        when(restTemplate.getForObject(ADDRESS + QUERY_SUFFIX, String.class, PROMETHEUS_QUERY)).thenReturn(PROMETHEUS_VALUE);
        String queryResult = QueryProvider.query(PROMETHEUS_QUERY, ADDRESS, restTemplate);

        //then
        assertEquals(PROMETHEUS_VALUE, queryResult);
    }

    @Test
    void shouldReturnEmptyIfQueryIsNull() {
        //when
        String queryResult = QueryProvider.query(null, ADDRESS, restTemplate);

        //then
        assertEquals("{}", queryResult);
    }
}
