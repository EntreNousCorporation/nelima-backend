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

    /**
     * Réglages de la plateforme, en écriture.
     *
     * <p>Ces routes ne tombaient sous aucune règle explicite : elles héritaient donc du simple
     * « être authentifié », ce qui laissait un compte école — ou un parent — modifier des
     * paramètres globaux, dont le taux de commission. La lecture reste ouverte aux comptes
     * authentifiés : le taux est de toute façon affiché à chaque parent avant paiement.
     */
    public static final String[] PLATFORM_SETTINGS_WRITE_RESOURCES = new String[]{
            "/global-parameters/**",
            "/platform-settings/**"
    };

    /**
     * Console du parc. Elle expose le chiffre d'affaires de YPYit et les agrégats de chaque école
     * cliente : une école y verrait ceux de ses concurrentes.
     */
    public static final String[] PLATFORM_CONSOLE_RESOURCES = new String[]{
            "/dashboard/platform"
    };

    /**
     * Création d'écoles et de leur compte d'amorçage. C'est l'onboarding, fait par YPYit depuis le
     * back-office : ouvert à tout compte authentifié, il permettait à n'importe qui de créer un
     * établissement, et à un parent de s'en fabriquer un.
     *
     * <p>La <em>liste</em> des établissements reste ouverte, elle : l'application parent s'en sert
     * pour rattacher un enfant à son école, et elle n'expose qu'un annuaire — nom, site, logo.
     */
    public static final String[] PLATFORM_ONBOARDING_RESOURCES = new String[]{
            "/establishments",
            "/establishments/subsidiaries"
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
