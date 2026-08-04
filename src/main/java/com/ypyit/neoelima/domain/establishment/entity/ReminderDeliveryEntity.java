package com.ypyit.neoelima.domain.establishment.entity;

import com.ypyit.neoelima.common.entity.BaseEntity;
import com.ypyit.neoelima.domain.establishment.enums.ReminderChannel;
import com.ypyit.neoelima.domain.establishment.enums.ReminderOrigin;
import com.ypyit.neoelima.domain.user.entity.UserEntity;
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
import org.hibernate.Hibernate;

import java.io.Serial;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Trace d'un rappel envoyé à une famille pour une tranche.
 *
 * <p>Ce registre manquait, et son absence était assumée tant que le seul émetteur était le rappel
 * automatique : le pire qu'on risquait était un doublon de notification. Avec des campagnes lancées
 * à la main, et le SMS facturé à l'envoi, il devient la seule chose qui empêche de relancer deux
 * fois la même famille le même jour — et de le payer deux fois.
 *
 * <p>La journée fait partie de la clé, et non l'horodatage : deux envois à quelques minutes
 * d'intervalle sont un doublon, deux envois à une semaine d'intervalle ne le sont pas.
 */
@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "reminder_delivery", uniqueConstraints = {
        @UniqueConstraint(name = "uk_reminder_delivery_day",
                columnNames = {"installment_id", "recipient_id", "channel", "sent_on"})
})
public class ReminderDeliveryEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReminderChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReminderOrigin origin;

    /** Journée de l'envoi, part de la clé d'unicité. */
    @Column(name = "sent_on", nullable = false)
    private LocalDate sentOn;

    @Column(nullable = false)
    private Instant sentAt;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @ToString.Exclude
    @JoinColumn(name = "installment_id", referencedColumnName = "id")
    private InstallmentEntity installment;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @ToString.Exclude
    @JoinColumn(name = "recipient_id", referencedColumnName = "id")
    private UserEntity recipient;

    /** Campagne à l'origine de l'envoi ; nulle pour un rappel automatique. */
    @ManyToOne(fetch = FetchType.LAZY)
    @ToString.Exclude
    @JoinColumn(name = "campaign_id", referencedColumnName = "id")
    private ReminderCampaignEntity campaign;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        ReminderDeliveryEntity that = (ReminderDeliveryEntity) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
