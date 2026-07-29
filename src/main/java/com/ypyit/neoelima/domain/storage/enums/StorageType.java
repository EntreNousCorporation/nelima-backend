package com.ypyit.neoelima.domain.storage.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public enum StorageType {
    STATIC("static");

    @Getter
    private final String value;
}
