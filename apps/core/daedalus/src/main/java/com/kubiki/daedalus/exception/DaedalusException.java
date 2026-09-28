package com.kubiki.daedalus.exception;

public class DaedalusException extends RuntimeException {
    public DaedalusException(String message) {
        super(message);
    }

    public DaedalusException(String message, Throwable cause) {
        super(message, cause);
    }
}
