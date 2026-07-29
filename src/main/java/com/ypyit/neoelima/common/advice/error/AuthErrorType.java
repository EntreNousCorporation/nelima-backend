package com.ypyit.neoelima.common.advice.error;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public enum AuthErrorType {

    INVALID_GRANT("invalid_grant");

    @Getter
    private final String value;
}