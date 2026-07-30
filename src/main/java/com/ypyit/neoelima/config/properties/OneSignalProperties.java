package com.ypyit.neoelima.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Accès à OneSignal, alimenté par variables d'environnement.
 *
 * <p>La clé REST est un secret d'administration : elle autorise l'envoi à l'ensemble des abonnés de
 * l'application. Elle ne vit donc que dans l'environnement du serveur, jamais en base ni dans le
 * client — l'{@code appId}, lui, est public et embarqué dans l'application mobile.
 *
 * <p>Sans configuration complète, aucune notification n'est tentée et on le dit une fois : c'est le
 * cas en développement et en test, où aucun appareil réel n'est abonné.
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "notification.onesignal")
public class OneSignalProperties {

    private String appId;

    private String restApiKey;

    private String baseUrl = "https://api.onesignal.com";

    /** Délai au-delà duquel on renonce : une notification n'a pas à retenir un fil d'exécution. */
    private int timeoutSeconds = 10;

    public boolean isComplete() {
        return StringUtils.isNotBlank(this.appId) && StringUtils.isNotBlank(this.restApiKey);
    }
}
