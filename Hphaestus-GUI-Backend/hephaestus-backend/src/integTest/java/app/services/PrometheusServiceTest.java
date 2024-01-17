package app.services;

import app.exceptions.PrometheusServiceException;
import app.model.SelectedCustomQuery;
import app.model.SelectedQuery;
import io.github.hephaestusmetrics.model.queryresults.RawQueryResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.RestTemplate;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class PrometheusServiceTest {
    @Mock
    private RestTemplate restTemplate;

    @Mock
    private RestTemplateBuilder restTemplateBuilder;

    private static final String ADDRESS = "127.0.0.1:12345";
    private static final String LABELS_SUFFIX = "/api/v1/labels";
    private static final String VALUE_SUFFIX = "/api/v1/label/{my_label}/values";
    private static final String QUERY_SUFFIX = "/api/v1/query?query={my_query}";
    private static final String QUERY_TAG = "QUERY_TAG";
    private static final String PROMETHEUS_LABEL = "Test Prometheus Label";
    private static final String PROMETHEUS_VALUE = "Test Prometheus Value";
    private static final String PROMETHEUS_QUERY = "Test Prometheus Query";

    @BeforeEach
    void setUp() {
        lenient().when(restTemplateBuilder.build()).thenReturn(restTemplate);
    }

    @Test
    void invalidAddressTest() {
        //given
        PrometheusService prometheusService = new PrometheusService(new RestTemplateBuilder(), ADDRESS);

        //when
        String actualAddress = prometheusService.getPrometheusAddress();

        //then
        assertEquals("http://" + ADDRESS, actualAddress);
    }

    @ParameterizedTest
    @MethodSource("validAddressSource")
    void validAddressTest(String address) {
        //given
        PrometheusService prometheusService = new PrometheusService(new RestTemplateBuilder(), address);

        //when
        String actualAddress = prometheusService.getPrometheusAddress();

        //then
        assertEquals(address, actualAddress);
    }

    @Test
    void getLabelsJsonTest() {
        //given
        PrometheusService prometheusService = new PrometheusService(restTemplateBuilder, ADDRESS);

        //when
        when(restTemplate.getForObject(prometheusService.getPrometheusAddress() + LABELS_SUFFIX, String.class)).thenReturn(PROMETHEUS_LABEL);
        String labelsJson = prometheusService.getLabelsJson();

        //then
        assertEquals(PROMETHEUS_LABEL, labelsJson);
    }

    @Test
    void shouldThrowExceptionWhileGettingLabelsJson() {
        //given
        PrometheusService prometheusService = new PrometheusService(new RestTemplateBuilder(), ADDRESS);

        //then
        assertThrows(PrometheusServiceException.class, prometheusService::getLabelsJson);
    }

    @Test
    void getLabelValuesTest() {
        //given
        PrometheusService prometheusService = new PrometheusService(restTemplateBuilder, ADDRESS);

        //when
        when(restTemplate.getForObject(prometheusService.getPrometheusAddress() + VALUE_SUFFIX, String.class, PROMETHEUS_LABEL)).thenReturn(PROMETHEUS_VALUE);
        String labelValuesJson = prometheusService.getLabelValuesJson(PROMETHEUS_LABEL);

        //then
        assertEquals(PROMETHEUS_VALUE, labelValuesJson);
    }

    @Test
    void shouldThrowExceptionWhileGettingLabelValuesJson() {
        //given
        PrometheusService prometheusService = new PrometheusService(new RestTemplateBuilder(), ADDRESS);

        //then
        assertThrows(PrometheusServiceException.class, () -> prometheusService.getLabelValuesJson(PROMETHEUS_LABEL));
    }

    @Test
    void queryStringTest() {
        //given
        PrometheusService prometheusService = new PrometheusService(restTemplateBuilder, ADDRESS);

        //when
        when(restTemplate.getForObject(prometheusService.getPrometheusAddress() + QUERY_SUFFIX, String.class, PROMETHEUS_QUERY)).thenReturn(PROMETHEUS_VALUE);
        String queryResult = prometheusService.query(PROMETHEUS_QUERY);

        //then
        assertEquals(PROMETHEUS_VALUE, queryResult);
    }

    @Test
    void querySelectedQueryTest() {
        //given
        PrometheusService prometheusService = new PrometheusService(restTemplateBuilder, ADDRESS);
        SelectedQuery selectedQuery = new SelectedCustomQuery(QUERY_TAG, PROMETHEUS_QUERY);

        //when
        when(restTemplate.getForObject(prometheusService.getPrometheusAddress() + QUERY_SUFFIX, String.class, PROMETHEUS_QUERY)).thenReturn(PROMETHEUS_VALUE);
        RawQueryResult queryResult = prometheusService.query(selectedQuery);

        //then
        assertEquals(QUERY_TAG, queryResult.getTag());
        assertEquals(PROMETHEUS_VALUE, queryResult.getMetric());
    }

    private static Stream<String> validAddressSource() {
        return Stream.of(
                "http://" + ADDRESS,
                "https://" + ADDRESS
        );
    }
}
