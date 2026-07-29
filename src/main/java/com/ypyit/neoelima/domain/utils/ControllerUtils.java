package com.ypyit.neoelima.domain.utils;

import org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

public final class ControllerUtils {

    private ControllerUtils() {
        throw new UnsupportedOperationException("ControllerUtils may not be instantiated");
    }

    public static URI buildMvcPathComponent(final UUID pathValue, final Class<?> clazz) {
        return MvcUriComponentsBuilder
                .fromController(clazz)
                .path("/{id}")
                .buildAndExpand(pathValue)
                .toUri();
    }
}
