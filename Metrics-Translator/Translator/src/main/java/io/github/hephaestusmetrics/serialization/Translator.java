package io.github.hephaestusmetrics.serialization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.github.hephaestusmetrics.model.ResultType;
import io.github.hephaestusmetrics.model.queryresults.*;
import io.github.hephaestusmetrics.model.metrics.Metric;
import io.github.hephaestusmetrics.serialization.model.PartialQueryResult;

import java.util.*;

/**
 * The type Translator.
 */
public class Translator {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Partially deserialize partial query result.
     *
     * @param rawMetricString the raw metric string
     * @return the partial query result
     */
    public PartialQueryResult partiallyDeserialize(String rawMetricString) {
        JsonNode tree = readTree(rawMetricString);
        return partiallyDeserialize(tree);
    }

    /**
     * Partially deserialize partial query result.
     *
     * @param rawQueryResult the raw query result
     * @return the partial query result
     */
    public PartialQueryResult partiallyDeserialize(RawQueryResult rawQueryResult) {
        JsonNode tree = readTree(rawQueryResult.getMetric());
        return partiallyDeserialize(rawQueryResult.getTag(), tree);
    }

    /**
     * Partially deserialize partial query result.
     *
     * @param rawMetricTree the raw metric tree
     * @return the partial query result
     */
    public PartialQueryResult partiallyDeserialize(JsonNode rawMetricTree) {
        String tag = rawMetricTree.get("tag").asText();
        JsonNode metric = rawMetricTree.get("metric");
        return partiallyDeserialize(tag, metric);
    }

    /**
     * Partially deserialize partial query result.
     *
     * @param tag  the tag
     * @param tree the tree
     * @return the partial query result
     */
    public PartialQueryResult partiallyDeserialize(String tag, JsonNode tree) {
        JsonNode data = tree.get("data");
        ResultType resultType = ResultType.fromString(data.get("resultType").asText());
        ArrayNode result = (ArrayNode) data.get("result");
        return new PartialQueryResult(tag, resultType, result);
    }

    /**
     * Parse result abstract query result.
     *
     * @param rawMetricString the raw metric string
     * @return the abstract query result
     */
    public AbstractQueryResult parseResult(String rawMetricString) {
       PartialQueryResult partial = partiallyDeserialize(rawMetricString);
       return parseResult(partial);
    }

    /**
     * Parse result abstract query result.
     *
     * @param rawQueryResult the raw query result
     * @return the abstract query result
     */
    public AbstractQueryResult parseResult(RawQueryResult rawQueryResult) {
        PartialQueryResult partial = partiallyDeserialize(rawQueryResult);
        return parseResult(partial);
    }

    /**
     * Parse result abstract query result.
     *
     * @param partial the partial
     * @return the abstract query result
     */
    public AbstractQueryResult parseResult(PartialQueryResult partial) {
        if (partial.getType() == ResultType.STRING) {
            return parseStringResult(partial);
        } else if (partial.getType() == ResultType.SCALAR) {
            return parseScalarResult(partial);
        } else if (partial.getType() == ResultType.VECTOR) {
            return parseVectorResult(partial);
        } else if (partial.getType() == ResultType.MATRIX) {
            return parseMatrixResult(partial);
        } else {
            throw new RuntimeException("Unrecognized result type: " + partial.getType());
        }
    }

    /**
     * Parse string result string query result.
     *
     * @param rawMetricString the raw metric string
     * @return the string query result
     */
    public StringQueryResult parseStringResult(String rawMetricString) {
        PartialQueryResult partial = partiallyDeserialize(rawMetricString);
        return parseStringResult(partial);
    }

    /**
     * Parse string result string query result.
     *
     * @param rawQueryResult the raw query result
     * @return the string query result
     */
    public StringQueryResult parseStringResult(RawQueryResult rawQueryResult) {
        PartialQueryResult partial = partiallyDeserialize(rawQueryResult);
        return parseStringResult(partial);
    }

    /**
     * Parse string result string query result.
     *
     * @param partial the partial
     * @return the string query result
     */
    public StringQueryResult parseStringResult(PartialQueryResult partial) {
        return new StringQueryResult(
                partial.getTag(),
                partial.getResult().get(0).asDouble(),
                partial.getResult().get(1).asText());
    }

    /**
     * Parse scalar result scalar query result.
     *
     * @param rawMetricString the raw metric string
     * @return the scalar query result
     */
    public ScalarQueryResult parseScalarResult(String rawMetricString) {
        PartialQueryResult partial = partiallyDeserialize(rawMetricString);
        return parseScalarResult(partial);
    }

    /**
     * Parse scalar result scalar query result.
     *
     * @param rawQueryResult the raw query result
     * @return the scalar query result
     */
    public ScalarQueryResult parseScalarResult(RawQueryResult rawQueryResult) {
        PartialQueryResult partial = partiallyDeserialize(rawQueryResult);
        return parseScalarResult(partial);
    }

    /**
     * Parse scalar result scalar query result.
     *
     * @param partial the partial
     * @return the scalar query result
     */
    public ScalarQueryResult parseScalarResult(PartialQueryResult partial) {
        return new ScalarQueryResult(
                partial.getTag(),
                partial.getResult().get(0).asDouble(),
                partial.getResult().get(1).asText());
    }

    /**
     * Parse vector result vector query result.
     *
     * @param rawMetricString the raw metric string
     * @return the vector query result
     */
    public VectorQueryResult parseVectorResult(String rawMetricString) {
        PartialQueryResult partial = partiallyDeserialize(rawMetricString);
        return parseVectorResult(partial);
    }

    /**
     * Parse vector result vector query result.
     *
     * @param rawQueryResult the raw query result
     * @return the vector query result
     */
    public VectorQueryResult parseVectorResult(RawQueryResult rawQueryResult) {
        PartialQueryResult partial = partiallyDeserialize(rawQueryResult);
        return parseVectorResult(partial);
    }

    /**
     * Parse vector result vector query result.
     *
     * @param partial the partial
     * @return the vector query result
     */
    public VectorQueryResult parseVectorResult(PartialQueryResult partial) {
        List<Metric> parsedValues = new LinkedList<>();
        for (JsonNode node : partial.getResult()) {
            parsedValues.add(new Metric(
                    partial.getTag(),
                    ResultType.VECTOR,
                    toMap(node.get("metric")),
                    node.get("value").get(0).asDouble(),
                    node.get("value").get(1).asText()
            ));
        }

        return new VectorQueryResult(partial.getTag(), parsedValues);
    }

    /**
     * Parse matrix result matrix query result.
     *
     * @param rawMetricString the raw metric string
     * @return the matrix query result
     */
    public MatrixQueryResult parseMatrixResult(String rawMetricString) {
        PartialQueryResult partial = partiallyDeserialize(rawMetricString);
        return parseMatrixResult(partial);
    }

    /**
     * Parse matrix result matrix query result.
     *
     * @param rawQueryResult the raw query result
     * @return the matrix query result
     */
    public MatrixQueryResult parseMatrixResult(RawQueryResult rawQueryResult) {
        PartialQueryResult partial = partiallyDeserialize(rawQueryResult);
        return parseMatrixResult(partial);
    }

    /**
     * Parse matrix result matrix query result.
     *
     * @param partial the partial
     * @return the matrix query result
     */
    public MatrixQueryResult parseMatrixResult(PartialQueryResult partial) {
        List<List<Metric>> parsedValues = new LinkedList<>();
        for (JsonNode node : partial.getResult()) {
            List<Metric> matrixRow = new LinkedList<>();
            Map<String, String> labels = toMap(node.get("metric"));
            for (JsonNode valuesNode : node.get("values")) {
                matrixRow.add(new Metric(
                        partial.getTag(),
                        ResultType.MATRIX,
                        labels,
                        valuesNode.get(0).asDouble(),
                        valuesNode.get(1).asText()));
            }
            parsedValues.add(matrixRow);
        }

        return new MatrixQueryResult(partial.getTag(), parsedValues);
    }

    private JsonNode readTree(String json) {
        // wrapper for checked exceptions
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String, String> toMap(JsonNode node) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        for (Iterator<Map.Entry<String, JsonNode>> it = node.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> field = it.next();
            map.put(field.getKey(), field.getValue().asText());
        }

        return map;
    }

}
