package com.ypyit.neoelima.common.exception;

public class NonUniqueUserFoundException extends CommonsException {

    public NonUniqueUserFoundException() {
        super();
    }

    public NonUniqueUserFoundException(String message) {
        super(message);
    }

    public NonUniqueUserFoundException(Throwable throwable) {
        super(throwable);
    }

    public NonUniqueUserFoundException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
