package app.controllers;

import app.model.SelectedFilters;
import app.services.PrometheusService;
import app.services.QueryBuilderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest
@AutoConfigureMockMvc
public class PrometheusControllerTest {
    @Value("${prometheus.address}")
    private String PROMETHEUS_ADDRESS;

    private static final String LABELS = "Test Labels";
    private static final String KEY = "Test Key";
    private static final String VALUE = "Test Value";
    private static final String QUERY = "Test Query";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PrometheusService prometheusService;

    @Autowired
    private QueryBuilderService queryBuilderService;

    @Test
    void getAddressTest() throws Exception {
        when(prometheusService.getPrometheusAddress()).thenReturn(PROMETHEUS_ADDRESS);

        mockMvc.perform(get("/prometheus/address"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(PROMETHEUS_ADDRESS)));
    }

    @Test
    void getLabelsTest() throws Exception {
        when(prometheusService.getLabelsJson()).thenReturn(LABELS);

        mockMvc.perform(get("/prometheus/labels"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(LABELS)));
    }

    @Test
    void getLabelValuesTest() throws Exception {
        when(prometheusService.getLabelValuesJson(KEY)).thenReturn(VALUE);

        mockMvc.perform(get("/prometheus/values")
                        .param("label", KEY))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(VALUE)));
    }

    @Test
    void postQueryTest() throws Exception {
        when(prometheusService.query(QUERY)).thenReturn(VALUE);

        mockMvc.perform(post("/prometheus/query")
                        .content(QUERY))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(VALUE)));
    }

    @Test
    void postQueryWithFiltersTest() throws Exception {
        Map<String, String> filters = new HashMap<>(){{
            put(KEY, VALUE);
        }};
        SelectedFilters selectedFilters = new SelectedFilters(filters);
        when(prometheusService.query(selectedFilters.getQueryString())).thenReturn(VALUE);
        mockMvc.perform(post("/prometheus/query/filters")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .accept(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content("{\"filters\":{\"" + KEY + "\":\"" + VALUE + "\"}}"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(VALUE)));
    }
}
