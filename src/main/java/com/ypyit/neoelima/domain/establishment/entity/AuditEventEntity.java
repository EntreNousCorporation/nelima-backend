package com.ypyit.neoelima.domain.establishment.entity;

import com.ypyit.neoelima.common.entity.BaseEntity;
import com.ypyit.neoelima.domain.establishment.enums.AuditAction;
import com.ypyit.neoelima.domain.user.entity.UserEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.hibernate.Hibernate;

import java.io.Serial;
import java.time.Instant;
import java.util.Objects;

/**
 * Une ligne du journal d'audit.
 *
 * <p>L'auteur est gardé <strong>deux fois</strong> : par sa référence, pour filtrer, et par son nom
 * recopié au moment des faits. Un compte fermé ou renommé ne doit pas effacer qui a encaissé —
 * c'est précisément quand quelqu'un s'en va qu'on relit le journal.
 */
@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "audit_event")
public class AuditEventEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 48)
    private AuditAction action;

    @Column(nullable = false)
    private Instant occurredAt;

    /** Nom de l'auteur au moment des faits. */
    @Column(length = 160)
    private String actorName;

    /** Ce sur quoi l'action a porté, en clair : un matricule, un frais, un nom de campagne. */
    @Column(length = 255)
    private String target;

    /** Précision utile à la relecture : un montant, un nombre de lignes, un rôle accordé. */
    @Column(length = 500)
    private String details;

    @ManyToOne(fetch = FetchType.LAZY)
    @ToString.Exclude
    @JoinColumn(name = "actor_id", referencedColumnName = "id")
    private UserEntity actor;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @ToString.Exclude
    @JoinColumn(name = "establishment_id", referencedColumnName = "id")
    private EstablishmentEntity establishment;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        AuditEventEntity that = (AuditEventEntity) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
