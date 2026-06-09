package com.projectboard.api;

public final class NotFoundException extends AppException {
    public NotFoundException(String message) {
        super("NOT_FOUND", message);
    }
}
