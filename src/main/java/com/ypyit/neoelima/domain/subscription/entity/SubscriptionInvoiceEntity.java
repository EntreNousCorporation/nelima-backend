package com.ypyit.neoelima.domain.subscription.entity;

import com.ypyit.neoelima.common.entity.BaseEntity;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Facture d'abonnement émise par YPYit à une école.
 *
 * <p><strong>C'est une pièce, pas une vue.</strong> La formule et le montant sont recopiés à
 * l'émission et n'en bougent plus : renégocier un tarif ne doit pas réécrire les factures déjà
 * émises, ni le chiffre d'affaires des mois passés.
 *
 * <p>Aucun statut n'est stocké. « Payée » se lit à {@code paidAt}, « en retard » se déduit de
 * {@code dueAt} et de la date du jour. Une colonne de statut supposerait un travail programmé pour
 * la tenir à jour, et un statut périmé est pire que pas de statut.
 */
@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "subscription_invoice", uniqueConstraints = {
        @UniqueConstraint(name = "uk_subscription_invoice_number", columnNames = {"number"}),
        @UniqueConstraint(name = "uk_subscription_invoice_period",
                columnNames = {"establishment_id", "period_start"})
})
public class SubscriptionInvoiceEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Numéro continu et sans doublon, de la forme {@code NL-2026-0001}. */
    @Column(nullable = false, length = 24)
    private String number;

    @Column(name = "plan", nullable = false, length = 32, updatable = false)
    private String plan;

    /**
    * Libellé de la formule au jour de l'émission.
    *
    * <p>Figé comme le montant : une formule renommée ou retirée ne doit pas réécrire une
    * pièce déjà émise sous l'ancien nom.
    */
    @Column(name = "plan_label", nullable = false, length = 80, updatable = false)
    private String planLabel;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDate periodStart;

    @Column(nullable = false)
    private LocalDate periodEnd;

    @Column(nullable = false)
    private Instant issuedAt;

    /** Date limite de règlement. Au-delà, la facture est en retard — sans que rien ne soit écrit. */
    @Column(nullable = false)
    private LocalDate dueAt;

    /** Nul tant que le règlement n'a pas été constaté. */
    private Instant paidAt;

    /** Virement, espèces, mobile money… saisi en clair : YPYit constate, il n'encaisse pas ici. */
    @Column(length = 64)
    private String paymentMethod;

    @Column(length = 120)
    private String paymentReference;

    /**
     * Facture annulée : conservée, jamais supprimée.
     *
     * <p>Une suite comptable doit rester continue. Retirer une ligne ferait un trou qu'aucun
     * contrôle ne saurait expliquer.
     */
    @Column(nullable = false)
    private boolean cancelled;

    @Column(length = 255)
    private String cancellationReason;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @ToString.Exclude
    @JoinColumn(name = "establishment_id", referencedColumnName = "id")
    private EstablishmentEntity establishment;

    /** Réglée : la seule question que la colonne sait trancher seule. */
    public boolean isPaid() {
        return Objects.nonNull(this.paidAt);
    }

    /** En retard : échéance dépassée, ni réglée ni annulée. */
    public boolean isLate(LocalDate today) {
        return !this.cancelled && !this.isPaid() && this.dueAt.isBefore(today);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        SubscriptionInvoiceEntity that = (SubscriptionInvoiceEntity) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
