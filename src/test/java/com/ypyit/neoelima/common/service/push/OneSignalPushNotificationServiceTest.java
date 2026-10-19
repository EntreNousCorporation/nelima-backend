package com.ypyit.neoelima.common.service.push;

import com.ypyit.neoelima.config.properties.OneSignalProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * OneSignal répond 200 même quand il ne livre rien : le journal doit dire ce qu'il a répondu,
 * pas affirmer un envoi.
 */
@ExtendWith(OutputCaptureExtension.class)
class OneSignalPushNotificationServiceTest {

    @Test
    @DisplayName("un envoi suspendu par OneSignal (quota dépassé) est journalisé comme tel")
    void reportsSuppressedDelivery(CapturedOutput output) {
        OneSignalPushNotificationService service = this.service("""
                {"id":"a000be56","errors":{"invalid_aliases":{"external_id":["x"]}},
                 "warnings":["Mobile push delivery was suppressed because your organization is over its free monthly active users limit."]}
                """);
        service.send(List.of(UUID.randomUUID()), "Cantine", "Inscrit", Map.of());
        assertThat(output).contains("PUSH_SUPPRESSED").contains("monthly active users").doesNotContain("PUSH_SENT");
    }

    @Test
    @DisplayName("un envoi accepté reste journalisé comme envoyé")
    void reportsSentDelivery(CapturedOutput output) {
        this.service("{\"id\":\"b1f30946\"}").send(List.of(UUID.randomUUID()), "Cantine", "Inscrit", Map.of());
        assertThat(output).contains("PUSH_SENT").doesNotContain("PUSH_SUPPRESSED");
    }

    private OneSignalPushNotificationService service(String response) {
        OneSignalProperties properties = new OneSignalProperties();
        properties.setAppId("app");
        properties.setRestApiKey("key");
        RestClient.Builder builder = RestClient.builder().baseUrl(properties.getBaseUrl());
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.onesignal.com/notifications"))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        return new OneSignalPushNotificationService(properties, builder.build());
    }
}
