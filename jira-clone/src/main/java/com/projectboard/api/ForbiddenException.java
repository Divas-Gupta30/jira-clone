package com.projectboard.api;

public final class ForbiddenException extends AppException {
    public ForbiddenException(String message) {
        super("FORBIDDEN", message);
    }
}
