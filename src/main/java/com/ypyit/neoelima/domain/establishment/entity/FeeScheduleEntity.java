package com.ypyit.neoelima.domain.establishment.entity;

import com.ypyit.neoelima.common.entity.BaseEntity;
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
import java.time.LocalDate;
import java.util.Objects;

/**
 * Tranche telle que l'école la définit sur un frais : « 1er versement, 50 000 FCFA, avant le
 * 15 octobre ». C'est un gabarit, pas une dette : il est décliné en {@link InstallmentEntity}
 * pour chaque élève concerné par le frais.
 *
 * <p>La somme des tranches d'un frais doit valoir le prix du frais ; c'est le service qui en
 * répond, la contrainte n'étant pas exprimable en SQL.
 */
@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "fee_schedule", uniqueConstraints = {
        @UniqueConstraint(name = "uk_fee_schedule_fee_position", columnNames = {"fee_id", "position"})
})
public class FeeScheduleEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    private String label;

    @Column(nullable = false)
    private BigDecimal amount;

    /** Échéance de la tranche. Le rappel au parent est déclenché à son approche. */
    private LocalDate dueDate;

    /** Rang de la tranche dans le frais, à partir de 1. */
    @Column(name = "position", nullable = false)
    private Integer position;

    /*
     * LAZY : `fee` était atteint deux fois dans le chargement d'une tranche — par le frais de
     * l'élève et par l'échéancier — et chaque occurrence traînait tout son établissement, avec son
     * adresse et son image de couverture. Huit des vingt-deux jointures pour deux lignes changées.
     *
     * Le règlement ne le lit pas : `ReceiptIssuer` prend le libellé sur la tranche elle-même.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @ToString.Exclude
    @JoinColumn(name = "fee_id", referencedColumnName = "id")
    private FeeEntity fee;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        FeeScheduleEntity that = (FeeScheduleEntity) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
