package com.ypyit.neoelima.domain.user.form;

import com.ypyit.neoelima.domain.establishment.enums.NotificationChannel;
import com.ypyit.neoelima.domain.establishment.enums.NotificationEvent;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Un interrupteur, et un seul : le client ne renvoie pas la matrice entière. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParentNotificationPreferenceForm {

    @NotNull
    private NotificationEvent event;

    @NotNull
    private NotificationChannel channel;

    private boolean enabled;
}
