package com.projectboard.api;

public final class ConflictException extends AppException {
    private final Object current;

    public ConflictException(String message) {
        this(message, null);
    }

    public ConflictException(String message, Object current) {
        super("CONFLICT", message);
        this.current = current;
    }

    public Object current() {
        return current;
    }
}
