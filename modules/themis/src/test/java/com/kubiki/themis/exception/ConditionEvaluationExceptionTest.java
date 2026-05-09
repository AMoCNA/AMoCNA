package com.kubiki.themis.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("ConditionEvaluationException Tests")
class ConditionEvaluationExceptionTest {

    @Test
    @DisplayName("should preserve message when created with message only")
    void shouldPreserveMessage() {
        String message = "test message";
        ConditionEvaluationException exception = new ConditionEvaluationException(message);
        assertEquals(message, exception.getMessage());
    }

    @Test
    @DisplayName("should preserve message and cause when created with both")
    void shouldPreserveMessageAndCause() {
        String message = "test message";
        Throwable cause = new RuntimeException("cause");
        ConditionEvaluationException exception = new ConditionEvaluationException(message, cause);
        assertEquals(message, exception.getMessage());
        assertEquals(cause, exception.getCause());
    }
}
