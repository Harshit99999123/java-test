package com.example.tasktracker.service;

/**
 * A request field broke a rule that can only be checked against the stored task.
 * Mapped to a 400 with {@code errors[field] = message}.
 */
public class InvalidTaskException extends RuntimeException {

    private final String field;

    public InvalidTaskException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
