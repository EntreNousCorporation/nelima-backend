package com.ypyit.neoelima.domain.payment.entity;

import com.ypyit.neoelima.common.entity.BaseEntity;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.hibernate.Hibernate;

import java.io.Serial;
import java.util.Objects;

/**
 * Compteur de reçus d'un établissement.
 *
 * <p>Une ligne par établissement, verrouillée en {@code SELECT ... FOR UPDATE} le temps
 * d'incrémenter. Une séquence PostgreSQL serait plus simple mais laisse des trous en cas de
 * rollback, ce qu'une numérotation comptable ne tolère pas ; et il en faudrait une par
 * établissement, créée dynamiquement.
 */
@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "receipt_counter", uniqueConstraints = {
        @UniqueConstraint(name = "uk_receipt_counter_establishment", columnNames = {"establishment_id"})
})
public class ReceiptCounterEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @Builder.Default
    @Column(name = "last_sequence", nullable = false)
    private Long lastSequence = 0L;

    @OneToOne(optional = false)
    @ToString.Exclude
    @JoinColumn(name = "establishment_id", referencedColumnName = "id")
    private EstablishmentEntity establishment;

    public long nextSequence() {
        this.lastSequence = this.lastSequence + 1;
        return this.lastSequence;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        ReceiptCounterEntity that = (ReceiptCounterEntity) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
