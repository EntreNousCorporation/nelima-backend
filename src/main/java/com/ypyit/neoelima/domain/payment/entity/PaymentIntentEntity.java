package com.ypyit.neoelima.domain.payment.entity;

import com.ypyit.neoelima.common.entity.BaseEntity;
import com.ypyit.neoelima.domain.establishment.entity.InstallmentEntity;
import com.ypyit.neoelima.domain.payment.enums.PaymentChannel;
import com.ypyit.neoelima.domain.payment.enums.PaymentIntentStatus;
import com.ypyit.neoelima.domain.user.entity.UserEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
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
 * Tentative de règlement d'une tranche.
 *
 * <p>{@code internalReference} porte la référence PaySwitch ({@code PSW-XXXXXXXXXX}) et est
 * <strong>unique</strong> : c'est cette contrainte qui rend le traitement du webhook idempotent.
 * Les agrégateurs rejouent leurs notifications — Jeko réessaie trois fois sur une vingtaine de
 * minutes — et sans elle un même encaissement produirait deux reçus.
 *
 * <p>Le montant est décomposé pour que l'école et YPYit soient réconciliables séparément :
 * {@code amountSchool} revient à l'établissement, {@code amountCommission} à YPYit, et le parent
 * est débité de la somme des deux.
 */
@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "payment_intent")
public class PaymentIntentEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Référence pivot avec PaySwitch. Nulle pour un encaissement hors ligne, qui ne transite pas
     * par l'agrégateur — d'où un index unique partiel plutôt qu'une colonne NOT NULL.
     */
    @Column(name = "internal_reference", length = 64)
    private String internalReference;

    @Column(nullable = false)
    private BigDecimal amountSchool;

    @Builder.Default
    @Column(nullable = false)
    private BigDecimal amountCommission = BigDecimal.ZERO;

    @Builder.Default
    @Column(nullable = false, length = 3)
    private String currency = "XOF";

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentIntentStatus status = PaymentIntentStatus.PENDING;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentChannel channel = PaymentChannel.ONLINE;

    /** Renseigné par l'agrégateur via PaySwitch. */
    private String providerType;

    @Column(length = 2048)
    private String checkoutUrl;

    private Instant settledAt;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @ToString.Exclude
    @JoinColumn(name = "installment_id", referencedColumnName = "id")
    private InstallmentEntity installment;

    /**
     * Auteur du règlement. Le paiement est découplé du tutorat : n'importe quel utilisateur
     * authentifié peut payer pour un élève, il n'a pas à en être le tuteur déclaré.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @ToString.Exclude
    @JoinColumn(name = "payer_user_id", referencedColumnName = "id")
    private UserEntity payer;

    /** Ce dont le parent est effectivement débité. */
    public BigDecimal totalAmount() {
        return this.amountSchool.add(
                Objects.isNull(this.amountCommission) ? BigDecimal.ZERO : this.amountCommission);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        PaymentIntentEntity that = (PaymentIntentEntity) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
