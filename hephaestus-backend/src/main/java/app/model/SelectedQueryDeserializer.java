package app.model;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

class SelectedQueryDeserializer extends StdDeserializer<SelectedQuery> {

    public SelectedQueryDeserializer() {
        super((Class<?>) null);
    }

    @Override
    public SelectedQuery deserialize(JsonParser jp, DeserializationContext ctx) throws IOException, JacksonException {
        // TODO only works with SelectedFilters
        JsonNode node = jp.readValueAsTree();

        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        for (Iterator<Map.Entry<String, JsonNode>> it = node.get("filters").fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> field = it.next();
            map.put(field.getKey(), field.getValue().asText());
        }

        return new SelectedFilters(map);
    }

}
