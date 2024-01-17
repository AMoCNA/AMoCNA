package app.volume;

import app.model.SelectedCustomQuery;
import app.model.SelectedQuery;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import responses.metrics.save.SaveMetricResponseEntity;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VolumeManagerTest {
    private static final String CONFIG_PATH = "./test_config";
    private static final String SAVED_PATH = "./test_saved";

    private static final String TAG_1 = "TAG_1";
    private static final String QUERY_STRING_1 = "QUERY_STRING_1";

    private static final String TAG_2 = "TAG_2";
    private static final String QUERY_STRING_2 = "QUERY_STRING_2";

    private static final HttpStatus EXPECTED_STATUS = HttpStatus.OK;
    private List<SelectedQuery> queries;

    @BeforeAll
    static void cleanUpBefore() {
        cleanUp();
    }

    @AfterEach
    void cleanUpAfter() {
        cleanUp();
    }

    @BeforeEach
    void initQueries() {
        SelectedQuery selectedQuery1 = new SelectedCustomQuery(TAG_1, QUERY_STRING_1);
        SelectedQuery selectedQuery2 = new SelectedCustomQuery(TAG_2, QUERY_STRING_2);
        queries = new ArrayList<>() {{
            add(selectedQuery1);
            add(selectedQuery2);
        }};
    }

    @Test
    void testIfFileExistsAfterSave() {
        //given
        VolumeManager volumeManager = new VolumeManager(SAVED_PATH, CONFIG_PATH);

        //when
        SaveMetricResponseEntity responseEntity = volumeManager.saveQueries(queries);
        File savedFile = new File(SAVED_PATH);

        //then
        assertEquals(EXPECTED_STATUS, responseEntity.getStatusCode());
        assertTrue(savedFile.isFile());
    }

    @Test
    void testIfFileContainsMetricsAfterSave() throws IOException {
        //given
        VolumeManager volumeManager = new VolumeManager(SAVED_PATH, CONFIG_PATH);

        //when
        volumeManager.saveQueries(queries);

        //then
        String fileContent = Files.readAllLines(Paths.get(SAVED_PATH)).get(0);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode chosenMetrics = mapper.readTree(fileContent).get("chosenMetrics");

        assertEquals(TAG_1, chosenMetrics.get(0).get("tag").asText());
        assertEquals(QUERY_STRING_1, chosenMetrics.get(0).get("queryString").asText());

        assertEquals(TAG_2, chosenMetrics.get(1).get("tag").asText());
        assertEquals(QUERY_STRING_2, chosenMetrics.get(1).get("queryString").asText());
    }

    @Test
    void loadMetricsFromSavedTest() {
        //given
        VolumeManager volumeManager = new VolumeManager(SAVED_PATH, CONFIG_PATH);

        //when
        volumeManager.saveQueries(queries);
        List<SelectedQuery> selectedQueries = volumeManager.loadQueries();

        //then
        assertEquals(TAG_1, selectedQueries.get(0).getTag());
        assertEquals(QUERY_STRING_1, selectedQueries.get(0).getQueryString());
        assertEquals(TAG_2, selectedQueries.get(1).getTag());
        assertEquals(QUERY_STRING_2, selectedQueries.get(1).getQueryString());
    }

    @Test
    void loadMetricsFromConfigTest() throws IOException {
        //given
        VolumeManager volumeManager = new VolumeManager(SAVED_PATH, CONFIG_PATH);
        File configMap = new File(CONFIG_PATH);
        FileWriter output = new FileWriter(configMap);
        output.write("{\"chosenMetrics\":[{\"tag\":\"" + TAG_1 + "\",\"queryString\":\"" + QUERY_STRING_1 + "\"}" +
                ",{\"tag\":\"" + TAG_2 + "\",\"queryString\":\"" + QUERY_STRING_2 + "\"}]}");
        output.close();

        //when
        List<SelectedQuery> selectedQueries = volumeManager.loadQueries();

        //then
        assertEquals(TAG_1, selectedQueries.get(0).getTag());
        assertEquals(QUERY_STRING_1, selectedQueries.get(0).getQueryString());
        assertEquals(TAG_2, selectedQueries.get(1).getTag());
        assertEquals(QUERY_STRING_2, selectedQueries.get(1).getQueryString());
    }

    private static void deleteFileFromPathIfExists(String path) {
        File savedFile = new File(path);
        if (savedFile.isFile()) {
            savedFile.delete();
        }
    }

    private static void cleanUp() {
        deleteFileFromPathIfExists(SAVED_PATH);
        deleteFileFromPathIfExists(CONFIG_PATH);
    }
}
