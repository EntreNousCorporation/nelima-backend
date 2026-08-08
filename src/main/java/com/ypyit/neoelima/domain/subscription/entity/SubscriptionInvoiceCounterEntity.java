package com.ypyit.neoelima.domain.subscription.entity;

import com.ypyit.neoelima.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.Hibernate;

import java.io.Serial;
import java.util.Objects;

/**
 * Compteur des factures d'abonnement, une ligne par année civile.
 *
 * <p>Même technique que {@code ReceiptCounterEntity} : la ligne est verrouillée en
 * {@code SELECT ... FOR UPDATE} le temps d'incrémenter. Une séquence PostgreSQL serait plus simple
 * mais laisse des trous en cas d'annulation de transaction, ce qu'une numérotation comptable ne
 * tolère pas.
 *
 * <p>YPYit émet quelques dizaines de factures par an : la contention est nulle. Le verrou ne coûte
 * donc rien, et il évite d'avoir à démontrer, le jour où le parc grandit, que deux émissions
 * simultanées ne peuvent pas partager un numéro.
 */
@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "subscription_invoice_counter", uniqueConstraints = {
        @UniqueConstraint(name = "uk_subscription_invoice_counter_year", columnNames = {"year"})
})
public class SubscriptionInvoiceCounterEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @Column(name = "year", nullable = false)
    private Integer year;

    @Builder.Default
    @Column(name = "last_sequence", nullable = false)
    private Long lastSequence = 0L;

    public long nextSequence() {
        this.lastSequence = this.lastSequence + 1;
        return this.lastSequence;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        SubscriptionInvoiceCounterEntity that = (SubscriptionInvoiceCounterEntity) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
