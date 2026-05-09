package com.kubiki.themis.exception;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConditionEvaluationExceptionTest {

    @Test
    void shouldPreserveMessage() {
        String message = "test message";
        ConditionEvaluationException exception = new ConditionEvaluationException(message);
        assertEquals(message, exception.getMessage());
    }

    @Test
    void shouldPreserveMessageAndCause() {
        String message = "test message";
        Throwable cause = new RuntimeException("cause");
        ConditionEvaluationException exception = new ConditionEvaluationException(message, cause);
        assertEquals(message, exception.getMessage());
        assertEquals(cause, exception.getCause());
    }
}
