package com.ypyit.neoelima.domain.user.dto;

import com.ypyit.neoelima.domain.establishment.enums.NotificationChannel;
import com.ypyit.neoelima.domain.establishment.enums.NotificationEvent;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;
import java.util.List;

/**
 * Ce qu'un parent accepte de recevoir, tel que son application le règle.
 *
 * <p>Ne contient que les événements qui produisent réellement une notification. Servir un
 * interrupteur qui ne coupe rien apprendrait à ne plus faire confiance aux interrupteurs.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParentNotificationSettingsDto {

    @Schema(description = "Un réglage par événement et par canal réellement raccordé")
    private List<EventSettingDto> events;

    /** Début des heures calmes, nul si le parent n'en a pas demandé. */
    private LocalTime quietFrom;

    private LocalTime quietTo;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EventSettingDto {
        private NotificationEvent event;
        private List<ChannelSettingDto> channels;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChannelSettingDto {
        private NotificationChannel channel;

        /** Ce que le parent a choisi, ou le défaut de l'événement s'il n'a rien choisi. */
        private boolean enabled;

        /**
         * Faux quand l'école a coupé ce canal.
         *
         * <p>L'interrupteur reste visible mais inopérant, avec sa raison : le masquer laisserait le
         * parent croire que l'application ne sait pas faire, alors que c'est son école qui a
         * décidé.
         */
        private boolean allowedBySchool;

        /** Verrouillé côté produit — le courriel qui porte un reçu ne se coupe pas. */
        private boolean locked;

        private String lockedReason;
    }
}
