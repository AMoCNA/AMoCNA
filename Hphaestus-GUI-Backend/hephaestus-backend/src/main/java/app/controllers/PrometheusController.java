package app.controllers;

import app.model.SelectedFilters;
import app.services.PrometheusService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import conf.Configuration;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.web.bind.annotation.*;

@RestController
@Log4j2
@RequiredArgsConstructor
@RequestMapping("prometheus")
@CrossOrigin(origins = Configuration.GUI_ORIGINS)
public class PrometheusController {

    private final PrometheusService prometheusService;
    private final ObjectMapper objectMapper;


    @RequestMapping(value = "/address", method = RequestMethod.GET, produces = "application/json")
    public String getAddress() throws JsonProcessingException {
        return objectMapper.writeValueAsString(prometheusService.getPrometheusAddress());
    }

    @RequestMapping(value = "/labels", method = RequestMethod.GET, produces = "application/json")
    public String getLabels() {
        return prometheusService.getLabelsJson();
    }

    @RequestMapping(value = "/values", method = RequestMethod.GET, produces = "application/json")
    public String getLabelValues(@RequestParam("label") String label) {
        return prometheusService.getLabelValuesJson(label);
    }

    @RequestMapping(value = "/query", method = RequestMethod.POST, produces = "application/json")
    public String postQuery(@RequestBody String query) {
        return prometheusService.query(query);
    }

    @RequestMapping(value = "/query/filters", method = RequestMethod.POST, produces = "application/json")
    public String postQueryFilters(@RequestBody SelectedFilters selectedFilters) {
        return prometheusService.query(selectedFilters.getQueryString());
    }

}
