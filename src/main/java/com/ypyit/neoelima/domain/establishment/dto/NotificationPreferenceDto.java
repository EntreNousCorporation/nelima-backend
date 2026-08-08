package com.ypyit.neoelima.domain.establishment.dto;

import com.ypyit.neoelima.domain.establishment.enums.NotificationChannel;
import com.ypyit.neoelima.domain.establishment.enums.NotificationEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * La matrice telle que l'écran l'affiche : un bloc par événement, une case par canal raccordé.
 *
 * <p>Les canaux non raccordés à l'événement n'y figurent pas — l'écran ne peut donc pas proposer
 * une case qui n'enverrait rien.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPreferenceDto {

    private NotificationEvent event;

    @Builder.Default
    private List<ChannelSettingDto> channels = new ArrayList<>();

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChannelSettingDto {

        private NotificationChannel channel;
        private boolean enabled;

        /**
         * Canal que l'école ne peut pas couper.
         *
         * <p>Affiché coché et inactif, avec sa raison, plutôt que retiré : le retirer laisserait
         * croire à un oubli.
         */
        private boolean locked;

        /** Pourquoi il est verrouillé, phrase montrée à côté de la case. */
        private String lockedReason;
    }
}
