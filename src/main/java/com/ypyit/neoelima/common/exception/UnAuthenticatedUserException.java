package com.ypyit.neoelima.common.exception;

public class UnAuthenticatedUserException extends CommonsException {

    public UnAuthenticatedUserException() {
        super();
    }

    public UnAuthenticatedUserException(String message) {
        super(message);
    }

    public UnAuthenticatedUserException(Throwable throwable) {
        super(throwable);
    }

    public UnAuthenticatedUserException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
