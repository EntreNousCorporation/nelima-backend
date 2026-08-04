package com.ypyit.neoelima.domain.establishment.entity;

import com.ypyit.neoelima.common.entity.BaseEntity;
import com.ypyit.neoelima.domain.establishment.enums.EnrollmentSource;
import com.ypyit.neoelima.domain.establishment.enums.EnrollmentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import java.time.Instant;
import java.util.Objects;

/**
 * Inscription d'un élève à une activité.
 *
 * <p>Une ligne par élève et par activité, garantie par la contrainte d'unicité : se réinscrire
 * après une annulation reprend la ligne existante. Empiler les demandes ferait compter deux fois le
 * même élève dans les places occupées.
 */
@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "activity_enrollment", uniqueConstraints = {
        @UniqueConstraint(name = "uk_activity_enrollment_activity_student",
                columnNames = {"activity_id", "student_id"})
})
public class ActivityEnrollmentEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private EnrollmentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private EnrollmentSource source;

    /**
     * Instant de la demande.
     *
     * <p>C'est lui qui ordonne la liste d'attente : la première place libérée revient à celui qui
     * a demandé le premier, et non au dernier que le secrétariat a sous les yeux.
     */
    @Column(nullable = false)
    private Instant requestedAt;

    @ManyToOne(optional = false)
    @ToString.Exclude
    @JoinColumn(name = "activity_id", referencedColumnName = "id")
    private ActivityEntity activity;

    @ManyToOne(optional = false)
    @ToString.Exclude
    @JoinColumn(name = "student_id", referencedColumnName = "id")
    private StudentEntity student;

    /**
     * Dette née de l'inscription, nulle tant qu'aucune place n'est obtenue.
     *
     * <p>On ne facture pas une place qu'on n'a pas : une inscription en liste d'attente ne porte
     * aucun frais, et c'est à la bascule que la dette apparaît.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @ToString.Exclude
    @JoinColumn(name = "student_fee_id", referencedColumnName = "id")
    private StudentFeeEntity studentFee;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        ActivityEnrollmentEntity that = (ActivityEnrollmentEntity) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
