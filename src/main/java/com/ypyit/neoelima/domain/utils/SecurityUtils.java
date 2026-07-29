package com.ypyit.neoelima.domain.utils;

public final class SecurityUtils {

    public static final String[] GLOBAL_RESOURCES = new String[]{
            "/actuator/**"
    };

    public static final String[] AUTH_RESOURCES = new String[]{
            "/auth/login",
            "/logs/anonymous",
            "/auth/introspection"
    };

    /**
     * Webhooks des agrégateurs, appelés de l'extérieur sans jeton Nelima. Ils s'authentifient par
     * la signature HMAC de leur charge utile, vérifiée par PaySwitch avant tout traitement.
     * Les laisser fermés reviendrait à répondre 401 à l'agrégateur, qui réessaierait puis
     * abandonnerait — l'encaissement serait perdu.
     */
    public static final String[] PAYMENT_WEBHOOK_RESOURCES = new String[]{
            "/payswitch/webhooks/**"
    };

    /**
     * Administration des agrégateurs : lecture et écriture des credentials, bascule du provider
     * actif. Le starter PaySwitch ne protège pas ces routes, c'est à l'application de le faire.
     */
    public static final String[] PAYMENT_ADMIN_RESOURCES = new String[]{
            "/payswitch/configs/**"
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
