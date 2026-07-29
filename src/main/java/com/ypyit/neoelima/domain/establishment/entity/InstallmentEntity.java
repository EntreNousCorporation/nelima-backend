package com.ypyit.neoelima.domain.establishment.entity;

import com.querydsl.core.annotations.QueryInit;
import com.ypyit.neoelima.common.entity.BaseEntity;
import com.ypyit.neoelima.domain.establishment.enums.InstallmentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Tranche due par un élève : la déclinaison d'une {@link FeeScheduleEntity} sur sa dette.
 *
 * <p>C'est l'unité de paiement du produit. Un parent règle une tranche d'un élève à la fois : il
 * n'existe pas de paiement couvrant plusieurs tranches en une seule transaction.
 */
@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "installment")
public class InstallmentEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    private String label;

    private BigDecimal amount;

    /** Échéance reprise de la tranche modèle au moment de la génération de la dette. */
    private LocalDate dueDate;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InstallmentStatus status = InstallmentStatus.PENDING;

    /** Horodatage de l'encaissement, en ligne comme au guichet. */
    private Instant paidAt;

    /**
     * {@code @QueryInit} est indispensable : QueryDSL n'initialise les chemins imbriqués que sur
     * une profondeur limitée. Sans lui, {@code installment.studentFee.student} vaut null dans le
     * métamodèle et toute requête filtrant sur l'établissement de l'élève échoue en
     * NullPointerException — à l'exécution seulement, la compilation ne signale rien.
     */
    @ManyToOne
    @ToString.Exclude
    @QueryInit("student.establishment")
    @JoinColumn(name = "student_fee_id", referencedColumnName = "id")
    private StudentFeeEntity studentFee;

    @ManyToOne
    @ToString.Exclude
    @JoinColumn(name = "fee_schedule_id", referencedColumnName = "id")
    private FeeScheduleEntity feeSchedule;

    private UUID paymentId;

    public boolean isSettled() {
        return InstallmentStatus.PAID.equals(this.status);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        InstallmentEntity that = (InstallmentEntity) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
