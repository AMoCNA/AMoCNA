package com.example.droolsprototype.demo.tests.utils;

import com.opencsv.CSVWriter;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class CSVUtils {

    public static void saveToCsv(String filePath, List<Object> values) {
        try (CSVWriter writer = new CSVWriter(new FileWriter(filePath, true))) {
            var data = values.stream().map(String::valueOf).toArray(String[]::new);
            writer.writeAll(Collections.singleton(data));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
