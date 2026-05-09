package com.kubiki.themis.exception;

public class MoaMappingException extends RuntimeException {
    public MoaMappingException(String message) {
        super(message);
    }
    public MoaMappingException(String message, Throwable cause) {
        super(message, cause);
    }
}
