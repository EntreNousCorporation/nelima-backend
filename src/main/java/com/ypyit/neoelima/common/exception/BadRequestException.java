package com.ypyit.neoelima.common.exception;

public class BadRequestException extends CommonsException {

    public BadRequestException() {
        super();
    }

    public BadRequestException(String message) {
        super(message);
    }

    public BadRequestException(Throwable throwable) {
        super(throwable);
    }

    public BadRequestException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
