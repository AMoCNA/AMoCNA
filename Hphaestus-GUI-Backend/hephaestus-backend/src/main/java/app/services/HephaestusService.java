package app.services;

import app.model.SelectedCustomQuery;
import app.model.SelectedFilters;
import app.model.SelectedQuery;
import app.volume.VolumeManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.hephaestusmetrics.model.queryresults.RawQueryResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.json.JSONArray;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Service
@Log4j2
@RequiredArgsConstructor
public class HephaestusService {

    private final VolumeManager volumeManager;
    private final PrometheusService prometheusService;
    private final ObjectMapper objectMapper;

    private List<SelectedFilters> selectedSimpleQueries = Collections.emptyList();
    private List<SelectedCustomQuery> selectedCustomQueries = Collections.emptyList();

    @PostConstruct
    public void loadQueriesFromVolume() {
        List<SelectedQuery> queries = volumeManager.loadQueries();
        selectedSimpleQueries = queries.stream()
                .filter(query -> query instanceof SelectedFilters)
                .map(query -> (SelectedFilters) query)
                .collect(Collectors.toList());
        selectedCustomQueries = queries.stream()
                .filter(query -> query instanceof SelectedCustomQuery)
                .map(query -> (SelectedCustomQuery) query)
                .collect(Collectors.toList());
    }

    public  ResponseEntity<Void> saveSimpleQueries(String body) {
        selectedSimpleQueries = parseQueries(body, SelectedFilters.class);
        return this.volumeManager.saveQueries(getAllSelectedQueries());
    }

    public  ResponseEntity<Void> saveCustomQueries(String body) {
        selectedCustomQueries = parseQueries(body, SelectedCustomQuery.class);
        return this.volumeManager.saveQueries(getAllSelectedQueries());
    }

    private <T> List<T> parseQueries(String body, Class<T> cls) {
        log.info("Save metrics: " + body);
        JSONArray array = new JSONArray(body);
        return IntStream.range(0, array.length())
                .mapToObj(array::getJSONObject)
                .map(Object::toString)
                .map(string -> {
                    try {
                        return objectMapper.readValue(string, cls);
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                })
                .collect(Collectors.toList());
    }

    private List<SelectedQuery> getAllSelectedQueries() {
        return Stream.concat(selectedCustomQueries.stream(), selectedSimpleQueries.stream()).collect(Collectors.toList());
    }

    public List<RawQueryResult> getSelected() {
        List<SelectedQuery> selectedQueries = getAllSelectedQueries();
        if (selectedQueries != null) {
            List<RawQueryResult> queryResults = selectedQueries.parallelStream()
                    .map(prometheusService::query)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            log.info("Returning {} selected metrics", queryResults.size());
            return queryResults;
        } else {
            log.info("No metrics selected - returning an empty list");
            return Collections.emptyList();
        }
    }

    public List<SelectedQuery> getSaved(boolean custom) {
        List<? extends SelectedQuery> selectedQueries = custom ? selectedCustomQueries : selectedSimpleQueries;
        log.info("Returning {} saved metrics", selectedQueries.size());
        return selectedQueries.stream().map(query -> (SelectedQuery) query).collect(Collectors.toList());
    }

}
