package app.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ExceptionControllerTest {
    private static final String EXCEPTION_MESSAGE = "EXCEPTION MESSAGE";
    private static final HttpStatus EXPECTED_STATUS = HttpStatus.INTERNAL_SERVER_ERROR;
    private static final String EXPECTED_BODY = "An error occurred";

    @Test
    void sanityTest() {
        //given
        ExceptionController exceptionController = new ExceptionController();
        Exception exception = new Exception(EXCEPTION_MESSAGE);

        //when
        ResponseEntity<?> responseEntity = exceptionController.illegalArgumentException(exception);

        //then
        assertEquals(EXPECTED_STATUS, responseEntity.getStatusCode());
        assertEquals(EXPECTED_BODY, responseEntity.getBody());
    }
}
