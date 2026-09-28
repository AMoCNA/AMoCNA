package com.kubiki.metis.knowledge;

public class KnowledgeBaseException extends Exception {

    public KnowledgeBaseException(String message) {
        super(message);
    }

    public KnowledgeBaseException(String message, Throwable cause) {
        super(message, cause);
    }
}
