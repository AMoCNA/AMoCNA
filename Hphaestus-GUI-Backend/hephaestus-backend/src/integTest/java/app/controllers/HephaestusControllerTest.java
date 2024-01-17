package app.controllers;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.io.File;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class HephaestusControllerTest {
    @Value("${config.path}")
    private String CONFIG_PATH;
    @Value("${saved.path}")
    private String SAVED_PATH;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void cleanUpBefore() {
        cleanUp();
    }

    @AfterEach
    void cleanUpAfter() {
        cleanUp();
    }

    @Test
    void getSelectedMetricsTest() throws Exception {
        mockMvc.perform(get("/hephaestus/metrics/selected"))
                .andDo(print())
                .andExpect(status().isOk());
    }

    @Test
    void saveSimpleQueriesTest() throws Exception {
        mockMvc.perform(put("/hephaestus/queries/simple")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .accept(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content("[]"))
                .andDo(print())
                .andExpect(status().isOk());
    }

    @Test
    void saveCustomQueriesTest() throws Exception {
        mockMvc.perform(put("/hephaestus/queries/custom")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .accept(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content("[]"))
                .andDo(print())
                .andExpect(status().isOk());
    }

    @Test
    void getSavedSimpleQueriesTest() throws Exception {
        mockMvc.perform(get("/hephaestus/queries/simple"))
                .andDo(print())
                .andExpect(status().isOk());
    }

    @Test
    void getSavedCustomQueriesTest() throws Exception {
        mockMvc.perform(get("/hephaestus/queries/custom"))
                .andDo(print())
                .andExpect(status().isOk());
    }

    private static void deleteFileFromPathIfExists(String path) {
        File savedFile = new File(path);
        if (savedFile.isFile()) {
            savedFile.delete();
        }
    }

    private void cleanUp() {
        deleteFileFromPathIfExists(SAVED_PATH);
        deleteFileFromPathIfExists(CONFIG_PATH);
    }
}
