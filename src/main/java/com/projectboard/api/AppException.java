package com.projectboard.api;

public sealed class AppException extends RuntimeException permits
        NotFoundException, ForbiddenException, ConflictException, ValidationException {

    private final String code;

    protected AppException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
