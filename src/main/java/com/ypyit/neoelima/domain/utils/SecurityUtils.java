package com.ypyit.neoelima.domain.utils;

public final class SecurityUtils {

    public static final String[] GLOBAL_RESOURCES = new String[]{
            "/actuator/**"
    };

    public static final String[] AUTH_RESOURCES = new String[]{
            "/auth/login",
            "/logs/anonymous",
            "/auth/introspection",
            "/payments/momo/call-back/*"
    };

    public static final String[] USER_POST_RESOURCES = new String[]{
            "/users/init-reset-password",
            "/users/mobile/init-reset-password",
            "/users/mobile",
            "/users/resend-signup-otp",
    };

    public static final String[] USER_PUT_RESOURCES = new String[]{
            "/users/reset-password",
            "/users/mobile/reset-password"
    };

    public static final String[] USER_GET_RESOURCES = new String[]{
            "/users/check-signup-otp/**",
    };

    public static final String[] SWAGGER_RESOURCES = new String[]{
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/swagger**",
            "/webjars/**",
            "/swagger-resources/**"
    };

    private SecurityUtils() {
        throw new UnsupportedOperationException("SecurityUtils may not be instantiated");
    }

}
