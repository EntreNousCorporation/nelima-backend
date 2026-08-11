package com.ypyit.neoelima.domain.establishment.entity;

import com.ypyit.neoelima.common.entity.BaseEntity;
import jakarta.persistence.Entity;
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

@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "student_fee")
public class StudentFeeEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;
    private String name;
    private boolean paid;
    private Instant deadline;
    @ManyToOne
    @ToString.Exclude
    @JoinColumn(name = "student_id", referencedColumnName = "id")
    private StudentEntity student;
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
        StudentFeeEntity that = (StudentFeeEntity) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
