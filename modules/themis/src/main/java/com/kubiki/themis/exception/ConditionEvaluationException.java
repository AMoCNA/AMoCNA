package com.kubiki.themis.exception;

public class ConditionEvaluationException extends RuntimeException {
    public ConditionEvaluationException(String message) {
        super(message);
    }

    public ConditionEvaluationException(String message, Throwable cause) {
        super(message, cause);
    }
}
