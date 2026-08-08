package com.ypyit.neoelima.domain.user.entity;

import com.ypyit.neoelima.common.entity.BaseEntity;
import com.ypyit.neoelima.domain.establishment.enums.NotificationChannel;
import com.ypyit.neoelima.domain.establishment.enums.NotificationEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.io.Serial;

/**
 * Ce qu'un parent accepte de recevoir.
 *
 * <p>À ne pas confondre avec {@code NotificationPreferenceEntity}, qui dit ce que l'<em>école</em>
 * accepte d'envoyer à ses familles. Les deux répondent à des questions différentes et se croisent
 * par un ET au moment de l'envoi : l'école autorise, le parent accepte. Aucune ne force l'autre —
 * une école ne peut pas imposer un SMS à qui n'en veut pas, et un parent ne peut pas s'abonner à ce
 * que son école n'envoie pas.
 *
 * <p>Matrice creuse : une ligne n'existe que si l'interrupteur a été touché. Un parent qui n'a
 * jamais ouvert l'écran retombe sur le défaut de l'événement, et ne perd rien.
 */
@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "user_notification_preference", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_notification_preference",
                columnNames = {"user_id", "event", "channel"})
})
public class UserNotificationPreferenceEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @ToString.Exclude
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private NotificationEvent event;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NotificationChannel channel;

    @Column(nullable = false)
    private boolean enabled;
}
