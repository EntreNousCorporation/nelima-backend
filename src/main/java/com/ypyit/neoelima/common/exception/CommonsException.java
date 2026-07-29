package com.ypyit.neoelima.common.exception;

public class CommonsException extends RuntimeException {

    public CommonsException() {
        super();
    }

    public CommonsException(String message) {
        super(message);
    }

    public CommonsException(Throwable throwable) {
        super(throwable);
    }

    public CommonsException(String message, Throwable throwable) {
        super(message, throwable);
    }

}
