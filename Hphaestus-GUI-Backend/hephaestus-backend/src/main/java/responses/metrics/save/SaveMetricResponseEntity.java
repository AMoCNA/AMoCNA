package responses.metrics.save;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

public class SaveMetricResponseEntity extends ResponseEntity<Void> {

    private HttpStatus status;
    private String message;

    public SaveMetricResponseEntity(HttpStatus status, String message) {
        super(status);
        this.message = message;
    }

    public Map<String, Object> toResponseMap() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("message", message);
        return parameters;
    }

}
