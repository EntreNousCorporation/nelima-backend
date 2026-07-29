package com.ypyit.neoelima.domain.utils;

import jakarta.servlet.http.HttpServletRequest;
import lombok.experimental.UtilityClass;

import java.util.Optional;

@UtilityClass
public class HeaderUtils {
    public static final String BASIC_HEADER = "Basic ";
    public static final String BEARER_HEADER = "Bearer ";

    public String extractToken(HttpServletRequest servletRequest) {
        Optional<String> authorization = Optional.ofNullable(servletRequest.getHeader("Authorization"));
        return authorization.map(s -> s.substring(BEARER_HEADER.length())).orElse("");
    }

    public String extractUserAgent(HttpServletRequest servletRequest) {
        return servletRequest.getHeader("User-Agent");
    }
}
