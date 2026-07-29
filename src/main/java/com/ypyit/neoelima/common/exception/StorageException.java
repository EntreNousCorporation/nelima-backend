package com.ypyit.neoelima.common.exception;

public class StorageException extends CommonsException {

    public StorageException() {
        super();
    }

    public StorageException(String message) {
        super(message);
    }

    public StorageException(Throwable throwable) {
        super(throwable);
    }

    public StorageException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
