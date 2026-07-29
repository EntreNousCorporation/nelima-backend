package com.ypyit.neoelima.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Credentials Jeko, alimentés par variables d'environnement.
 *
 * <p>PaySwitch conserve les credentials de l'agrégateur en base, en clair. Les tenir ici en fait
 * une simple projection de l'environnement du serveur : la base n'est plus la source de vérité,
 * et personne n'a à manipuler de secret via une API HTTP.
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "payswitch.jeko")
public class JekoProperties {

    private String apiKey;

    private String apiKeyId;

    private String webhookSecret;

    private String storeId;

    /** Jeko exige toujours un canal : soit ce défaut, soit un canal passé par transaction. */
    private String defaultPaymentMethod = "wave";

    private String environment = "SANDBOX";

    /** Sans les quatre credentials, il n'y a rien à amorcer. */
    public boolean isComplete() {
        return StringUtils.isNotBlank(this.apiKey)
                && StringUtils.isNotBlank(this.apiKeyId)
                && StringUtils.isNotBlank(this.webhookSecret)
                && StringUtils.isNotBlank(this.storeId);
    }
}
