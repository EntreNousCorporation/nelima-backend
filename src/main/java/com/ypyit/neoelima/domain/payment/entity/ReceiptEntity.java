package com.ypyit.neoelima.domain.payment.entity;

import com.ypyit.neoelima.common.entity.BaseEntity;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Reçu émis pour tout encaissement, en ligne comme au guichet.
 *
 * <p>Le numéro est séquentiel <strong>par établissement</strong> et sans trou : c'est une exigence
 * comptable, l'école doit pouvoir justifier la continuité de sa numérotation. La contrainte
 * d'unicité {@code (establishment_id, sequence_number)} en est le dernier rempart ; l'allocation
 * elle-même est sérialisée par {@link ReceiptCounterEntity}.
 *
 * <p>Les libellés d'élève et de payeur sont recopiés à l'émission plutôt que résolus par jointure :
 * un reçu est une pièce figée, il ne doit pas changer si l'élève est renommé ou change d'école.
 */
@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "receipt", uniqueConstraints = {
        @UniqueConstraint(name = "uk_receipt_establishment_sequence",
                columnNames = {"establishment_id", "sequence_number"})
})
public class ReceiptEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @Column(name = "sequence_number", nullable = false)
    private Long sequenceNumber;

    /** Numéro présenté au parent, dérivé de la séquence (par exemple {@code 2026-000042}). */
    @Column(nullable = false, length = 64)
    private String number;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private Instant issuedAt;

    private String studentLabel;

    private String studentRegistrationNumber;

    private String payerLabel;

    @ManyToOne(optional = false)
    @ToString.Exclude
    @JoinColumn(name = "establishment_id", referencedColumnName = "id")
    private EstablishmentEntity establishment;

    @OneToOne(optional = false)
    @ToString.Exclude
    @JoinColumn(name = "payment_intent_id", referencedColumnName = "id")
    private PaymentIntentEntity paymentIntent;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        ReceiptEntity that = (ReceiptEntity) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
