package com.ypyit.neoelima.domain.establishment.entity;

import com.ypyit.neoelima.common.entity.BaseEntity;
import com.ypyit.neoelima.domain.establishment.enums.ReminderChannel;
import com.ypyit.neoelima.domain.establishment.enums.ReminderTarget;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.hibernate.Hibernate;

import java.io.Serial;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Campagne de relance lancée depuis le portail.
 *
 * <p>Ce qu'elle a produit — familles ayant réglé, montant recouvré — n'est pas stocké : cela se
 * déduit des rappels qu'elle a émis et de l'état des tranches qu'ils visaient. Le figer obligerait
 * à le tenir à jour, et il divergerait le jour où une famille règle après coup.
 */
@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "reminder_campaign")
public class ReminderCampaignEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @Column(nullable = false, length = 128)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ReminderTarget target;

    /**
     * Canaux effectivement empruntés.
     *
     * <p>Une collection et non un canal unique : la maquette propose SMS et notification ensemble,
     * et le coût n'est pas le même selon qu'on coche l'un, l'autre ou les deux.
     */
    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "reminder_campaign_channel",
            joinColumns = @JoinColumn(name = "campaign_id"))
    @Column(name = "channel", nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    @EqualsAndHashCode.Exclude
    private Set<ReminderChannel> channels = new HashSet<>();

    /** Gabarit du message, avec ses variables : {parent}, {eleve}, {classe}, {montant}, {retard}. */
    @Column(nullable = false, length = 500)
    private String messageTemplate;

    @Column(nullable = false)
    private Instant sentAt;

    /** Nombre de rappels effectivement partis, doublons du jour déjà écartés. */
    @Column(nullable = false)
    private int sentCount;

    /** Destinataires écartés parce que déjà relancés le jour même. */
    @Column(nullable = false)
    private int skippedCount;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @ToString.Exclude
    @JoinColumn(name = "establishment_id", referencedColumnName = "id")
    private EstablishmentEntity establishment;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        ReminderCampaignEntity that = (ReminderCampaignEntity) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
