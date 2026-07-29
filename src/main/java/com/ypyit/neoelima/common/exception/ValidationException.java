package com.ypyit.neoelima.common.exception;

import lombok.Getter;

public class ValidationException extends CommonsException {

    @Getter
    private final String field;

    public ValidationException(String field, String message) {
        this(field, message, null);
    }

    public ValidationException(String field, String message, Throwable throwable) {
        super(message, throwable);
        this.field = field;
    }
}
