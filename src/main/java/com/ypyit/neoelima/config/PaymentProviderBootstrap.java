package com.ypyit.neoelima.config;

import com.ypy.paygw.payswitch.api.Environment;
import com.ypy.paygw.payswitch.api.ProviderType;
import com.ypy.paygw.payswitch.configs.ConfigsService;
import com.ypy.paygw.payswitch.configs.CreateProviderConfigRequest;
import com.ypy.paygw.payswitch.configs.ProviderConfigDto;
import com.ypy.paygw.payswitch.configs.UpdateProviderConfigRequest;
import com.ypyit.neoelima.config.properties.JekoProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Aligne la configuration de l'agrégateur sur l'environnement du serveur au démarrage.
 *
 * <p>PaySwitch attend que ses credentials soient enregistrés en base via son API REST, où ils
 * dorment en clair. Les injecter depuis l'environnement inverse la source de vérité : la valeur
 * qui fait foi est celle du serveur, la base n'en est qu'un reflet reconstruit à chaque démarrage.
 * Faire tourner les clés se réduit alors à changer une variable et redémarrer, sans exposer de
 * secret sur une route HTTP.
 *
 * <p>Sans credentials complets, on ne fait rien et on le dit : c'est le cas en développement et en
 * test, où aucun paiement réel n'est initié.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentProviderBootstrap {

    private static final String LABEL = "Jeko (piloté par variables d'environnement)";

    private final ConfigsService configsService;
    private final JekoProperties jekoProperties;

    @EventListener(ApplicationReadyEvent.class)
    public void synchronizeJekoConfiguration() {
        if (!this.jekoProperties.isComplete()) {
            log.warn("JEKO_CONFIG_SKIPPED: credentials incomplets, aucun fournisseur de paiement actif. "
                    + "Renseigner JEKO_API_KEY, JEKO_API_KEY_ID, JEKO_WEBHOOK_SECRET et JEKO_STORE_ID.");
            return;
        }

        Map<String, String> credentials = this.credentials();
        Map<String, String> settings = this.settings();
        Environment environment = Environment.valueOf(this.jekoProperties.getEnvironment().toUpperCase());

        Optional<ProviderConfigDto> existing = this.configsService.list().stream()
                .filter(config -> ProviderType.JEKO.equals(config.providerType()))
                .findFirst();

        ProviderConfigDto config = existing
                .map(current -> this.updateIfChanged(current, environment, credentials, settings))
                .orElseGet(() -> this.configsService.create(new CreateProviderConfigRequest(
                        ProviderType.JEKO, LABEL, environment, credentials, settings)));

        if (!config.active()) {
            this.configsService.activate(config.id());
            log.info("JEKO_CONFIG_ACTIVATED: configuration {} activée en environnement {}",
                    config.id(), environment);
        }
    }

    /**
     * On ne réécrit qu'en cas de divergence réelle : une écriture systématique à chaque démarrage
     * polluerait la piste d'audit du fournisseur sans rien changer.
     */
    private ProviderConfigDto updateIfChanged(ProviderConfigDto current,
                                              Environment environment,
                                              Map<String, String> credentials,
                                              Map<String, String> settings) {
        boolean unchanged = credentials.equals(current.credentials())
                && settings.equals(current.settings())
                && environment.equals(current.environment());
        if (unchanged) {
            log.info("JEKO_CONFIG_UNCHANGED: configuration {} déjà alignée sur l'environnement", current.id());
            return current;
        }
        log.info("JEKO_CONFIG_UPDATED: configuration {} réalignée sur les variables d'environnement",
                current.id());
        return this.configsService.update(current.id(),
                new UpdateProviderConfigRequest(LABEL, environment, credentials, settings));
    }

    private Map<String, String> credentials() {
        Map<String, String> credentials = new LinkedHashMap<>();
        credentials.put("api_key", this.jekoProperties.getApiKey());
        credentials.put("api_key_id", this.jekoProperties.getApiKeyId());
        credentials.put("webhook_secret", this.jekoProperties.getWebhookSecret());
        credentials.put("store_id", this.jekoProperties.getStoreId());
        return credentials;
    }

    private Map<String, String> settings() {
        Map<String, String> settings = new LinkedHashMap<>();
        settings.put("default_payment_method", this.jekoProperties.getDefaultPaymentMethod());
        settings.put("amount_multiplier", this.jekoProperties.getAmountMultiplier());
        return settings;
    }
}
