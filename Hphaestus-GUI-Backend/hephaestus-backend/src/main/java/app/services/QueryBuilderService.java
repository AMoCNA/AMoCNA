package app.services;

import lombok.extern.log4j.Log4j2;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Log4j2
public class QueryBuilderService {

    public static QueryBuilderService INSTANCE = new QueryBuilderService();

    @Nullable
    public String filtersToQuery(Map<String, String> filters){
        if (filters == null || filters.isEmpty()) {
            // query can't be constructed - no filters
            log.warn("Failed to construct query - no filters were selected");
            return null;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{");
        for (Map.Entry<String, String> entry : filters.entrySet()){
            sb.append(entry.getKey());
            sb.append("=\"");
            sb.append(entry.getValue());
            sb.append("\",");
        }
        sb.deleteCharAt(sb.length()-1);
        sb.append("}");
        return sb.toString();
    }

}
