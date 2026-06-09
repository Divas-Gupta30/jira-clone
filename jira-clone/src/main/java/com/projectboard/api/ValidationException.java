package com.projectboard.api;

import java.util.List;

public final class ValidationException extends AppException {
    private final List<String> allowedTransitions;

    public ValidationException(String message) {
        this(message, List.of());
    }

    public ValidationException(String message, List<String> allowedTransitions) {
        super("VALIDATION_ERROR", message);
        this.allowedTransitions = allowedTransitions;
    }

    public List<String> allowedTransitions() {
        return allowedTransitions;
    }
}
