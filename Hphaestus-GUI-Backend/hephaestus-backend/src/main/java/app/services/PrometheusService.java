package app.services;

import app.exceptions.PrometheusServiceException;
import app.model.SelectedQuery;
import io.github.hephaestusmetrics.model.queryresults.RawQueryResult;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Objects;

@Service
@Log4j2
public class PrometheusService {

    private final String prometheusAddress;

    private final RestTemplate restTemplate;

    public PrometheusService(RestTemplateBuilder restTemplateBuilder, @Value("${prometheus.address}") String prometheusAddress) {
        this.restTemplate = restTemplateBuilder.build();
        if (!prometheusAddress.startsWith("http://") && !prometheusAddress.startsWith("https://")){
            this.prometheusAddress = "http://" + prometheusAddress;
        } else {
            this.prometheusAddress = prometheusAddress;
        }
        log.info("Set prometheus address to {}", prometheusAddress);
    }

    public String getPrometheusAddress() {
        return prometheusAddress;
    }

    public String getLabelsJson() {
        String labelsAddress = getPrometheusAddress() + "/api/v1/labels";
        try {
            return restTemplate.getForObject(labelsAddress, String.class);
        } catch(Exception ex) {
          throw new PrometheusServiceException(
                  "Unexpected exception occurred while loading labels: " + ex.getMessage());
        }
    }

    public String getLabelValuesJson(String label) {
        try {
            return restTemplate.getForObject(
                    getPrometheusAddress() + "/api/v1/label/{my_label}/values", String.class, label);
        } catch(Exception ex) {
            throw new PrometheusServiceException(
                    "Unexpected exception occurred while loading labels' values: " + ex.getMessage());
        }
    }

    public String query(String query) {
        if (query == null){
            return null;
        }
        try {
        return restTemplate.getForObject(
                getPrometheusAddress() + "/api/v1/query?query={my_query}",
                String.class,
                query);
        } catch (Exception ex) {
            log.warn("Unexpected exception occurred while querying prometheus for query: " + query);
            return null;
        }
    }

    public RawQueryResult query(SelectedQuery query) {
        if (query == null){
            return null;
        }
        String result = query(query.getQueryString());
        return !Objects.isNull(result) ? new RawQueryResult(query.getTag(), result) : null;
    }

}
