package responses.metrics.save;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class SaveMetricResponseEntityTest {
    private final static HttpStatus HTTP_STATUS = HttpStatus.OK;

    private final static String MESSAGE = "message";

    @Test
    void responseMapTest() {
        //given
        SaveMetricResponseEntity saveMetricResponseEntity = new SaveMetricResponseEntity(HTTP_STATUS, MESSAGE);

        //when
        Map<String, Object> responseMap = saveMetricResponseEntity.toResponseMap();

        //then
        assertFalse(responseMap.isEmpty());
        assertEquals(MESSAGE, responseMap.get("message"));
    }
}
