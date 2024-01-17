package app.providers;

import org.springframework.web.client.RestTemplate;

public class QueryProvider {

    public static String query(String query, String prometheusAddress, RestTemplate restTemplate) {
        if (query == null){
            return "{}";
        }

        return restTemplate.getForObject(
                prometheusAddress + "/api/v1/query?query={my_query}",
                String.class,
                query);
    }
}
