package app.volume;

import app.exceptions.VolumeManagerException;
import app.model.SelectedQuery;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.log4j.Log4j2;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import responses.metrics.save.SaveMetricResponseEntity;

import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Service
@Log4j2
public class VolumeManager {

    private final String savedPath;

    private final String configPath;

    private final ObjectMapper mapper = new ObjectMapper();

    public VolumeManager(@Value("${saved.path}") String savedPath, @Value("${config.path}") String configPath) {
        this.savedPath = savedPath;
        this.configPath = configPath;
    }

    public SaveMetricResponseEntity saveQueries(List<SelectedQuery> queries){
        log.info("Attempting to save {} metrics", queries.size());
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("{\"chosenMetrics\":[");
        if (queries.size() > 0) {
            for (SelectedQuery query : queries) {
                try {
                    String queryStr = mapper.writeValueAsString(query);
                    stringBuilder.append(queryStr).append(",");
                } catch (JsonProcessingException e) {
                    log.error("Failed to save query " + query + ", skipping...", e);
                }
            }
            stringBuilder.deleteCharAt(stringBuilder.length()-1);
        }
        stringBuilder.append("]}");
        String resultString = stringBuilder.toString();
        try {
            File saveFile = new File(savedPath);
            //noinspection ResultOfMethodCallIgnored
            saveFile.getParentFile().mkdirs();
            FileWriter output = new FileWriter(saveFile);
            output.write(resultString);
            output.close();
        } catch (Exception e) {
            throw new VolumeManagerException("Unexpected exception occurred while saving metrics: " + e.getMessage());
        }
        log.info("Metrics saved successfully");
        return new SaveMetricResponseEntity(HttpStatus.OK, "Successfully saved");
    }

    /**
     * Attempts to load metrics from volume
     * @return metrics from volume or empty list
     */
    public List<SelectedQuery> loadQueries() {
        List<SelectedQuery> selectedQueries;
        selectedQueries = loadQueries(false);
        if (selectedQueries == null){
            selectedQueries = loadQueries(true);
        }
        if (selectedQueries == null){
            selectedQueries = new ArrayList<>();
        }
        log.info("Loaded total of {} metrics at start", selectedQueries.size());
        return selectedQueries;
    }

    private List<SelectedQuery> loadQueries(boolean fromConfigMap){
        String volumePath;
        volumePath = fromConfigMap ? configPath : savedPath;
        List<SelectedQuery> selectedQueries = new ArrayList<>();
        try {
            String jsonString = Files.readString(Paths.get(volumePath), StandardCharsets.US_ASCII);
            JSONArray jsonArr = new JSONObject(jsonString).getJSONArray("chosenMetrics");
            for (int i = 0; i < jsonArr.length(); i++) {
                String queriesStr = jsonArr.get(i).toString();
                SelectedQuery query = queryFromString(queriesStr);
                selectedQueries.add(query);
            }
        } catch (Exception e) {
            String source = fromConfigMap ? "config map" : "save file";
            log.warn(
                    "Attempted to load metrics from {} but failed. Resource path was {}",
                    source,
                    e.getMessage());
            return null;
        }
        return selectedQueries;
    }

    private SelectedQuery queryFromString(String queryStr) throws JsonProcessingException {
        return mapper.readValue(queryStr, SelectedQuery.class);
    }

}
