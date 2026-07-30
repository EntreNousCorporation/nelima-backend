package com.ypyit.neoelima.common.service.push;

import com.ypyit.neoelima.config.properties.OneSignalProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Mise en œuvre OneSignal du {@link PushNotificationService}.
 *
 * <p>Les destinataires sont désignés par leur identifiant Nelima, déclaré à OneSignal comme
 * <em>external id</em> au moment de la connexion dans l'application. C'est ce qui dispense de tenir
 * une table d'appareils côté serveur : le lien compte → abonnements vit chez OneSignal, qui suit
 * seul les réinstallations, les changements de téléphone et les jetons révoqués. Une table maison
 * dériverait silencieusement de la réalité.
 */
@Slf4j
@Service
public class OneSignalPushNotificationService implements PushNotificationService {

    private final OneSignalProperties properties;
    private final RestClient restClient;

    public OneSignalPushNotificationService(OneSignalProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.restClient = builder
                .baseUrl(properties.getBaseUrl())
                .requestFactory(timeoutFactory(properties.getTimeoutSeconds()))
                .build();
    }

    /**
     * Notifie les comptes désignés.
     *
     * @param recipients identifiants Nelima des comptes à joindre ; les absents d'OneSignal sont
     *                   simplement ignorés par celui-ci
     * @param data       charge utile transmise à l'application pour l'ouvrir au bon endroit
     */
    @Async
    @Override
    public void send(Collection<UUID> recipients, String title, String message, Map<String, String> data) {
        if (!this.properties.isComplete()) {
            log.debug("PUSH_SKIPPED: OneSignal non configuré, aucune notification envoyée");
            return;
        }
        Set<String> externalIds = new LinkedHashSet<>();
        recipients.stream().filter(Objects::nonNull).map(UUID::toString).forEach(externalIds::add);
        if (externalIds.isEmpty()) {
            log.debug("PUSH_SKIPPED: aucun destinataire pour « {} »", title);
            return;
        }

        try {
            this.restClient.post()
                    .uri("/notifications")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Key " + this.properties.getRestApiKey())
                    .body(this.payload(externalIds, title, message, data))
                    .retrieve()
                    .toBodilessEntity();
            log.info("PUSH_SENT: « {} » à {} destinataire(s)", title, externalIds.size());
        } catch (RuntimeException e) {
            // Volontairement avalé : la notification est un confort, pas une garantie.
            log.error("PUSH_FAILED: « {} » non envoyée à {} destinataire(s) : {}",
                    title, externalIds.size(), e.getMessage());
        }
    }

    private Map<String, Object> payload(Set<String> externalIds, String title, String message,
                                        Map<String, String> data) {
        Map<String, Object> body = new HashMap<>();
        body.put("app_id", this.properties.getAppId());
        // Ciblage par alias plutôt que par identifiant d'abonnement : un même compte peut avoir
        // plusieurs appareils, et OneSignal les résout tous.
        body.put("include_aliases", Map.of("external_id", List.copyOf(externalIds)));
        body.put("target_channel", "push");
        body.put("headings", Map.of("fr", title, "en", title));
        body.put("contents", Map.of("fr", message, "en", message));
        if (Objects.nonNull(data) && !data.isEmpty()) {
            body.put("data", data);
        }
        return body;
    }

    /**
     * Délais bornés explicitement. Sans eux, un appel qui reste ouvert immobiliserait un fil du
     * pool asynchrone, et il n'en faut pas beaucoup pour l'épuiser.
     */
    private static ClientHttpRequestFactory timeoutFactory(int seconds) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(seconds));
        factory.setReadTimeout(Duration.ofSeconds(seconds));
        return factory;
    }
}
