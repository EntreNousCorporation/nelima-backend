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
import java.util.Objects;

/**
 * Classe au sens scolaire : « CM1 A », rattachée à un niveau, dans une salle, avec un effectif
 * maximal.
 *
 * <p>Distincte du niveau, qui dit ce qu'on y enseigne : une école tient plusieurs CM1 et les
 * répartit entre plusieurs salles et plusieurs enseignants. C'est la classe, et non le niveau, que
 * le secrétariat manipule au quotidien.
 *
 * <p>Le titulaire est ici un simple nom. Le répertoire du personnel n'existe pas encore ; quand il
 * arrivera, ce champ deviendra une référence, et les noms saisis seront rapprochés des comptes.
 */
@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "school_class", uniqueConstraints = {
        @UniqueConstraint(name = "uk_school_class_establishment_name",
                columnNames = {"establishment_id", "name"})
})
public class SchoolClassEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @Column(nullable = false, length = 64)
    private String name;

    /** Salle affectée. Facultative : toutes les écoles ne nomment pas leurs salles. */
    @Column(length = 64)
    private String room;

    /**
     * Effectif maximal.
     *
     * <p>Il n'est pas imposé à l'inscription : une classe peut se retrouver en dépassement, et
     * refuser l'élève laisserait le secrétariat sans solution. Le portail le signale en rouge,
     * ce qui est le comportement attendu d'un indicateur de remplissage.
     */
    @Column(nullable = false)
    private Integer capacity;

    /**
     * Titulaire désigné dans le répertoire du personnel.
     *
     * <p>Facultatif : une classe peut attendre son titulaire, et la reprise n'a pu rattacher que
     * les noms qui désignaient un membre sans ambiguïté.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @ToString.Exclude
    @JoinColumn(name = "main_teacher_id", referencedColumnName = "id")
    private StaffEntity mainTeacher;

    /**
     * Nom du titulaire tel qu'il avait été saisi, avant que le répertoire n'existe.
     *
     * <p>Conservé comme repli : le supprimer perdrait les titulaires qu'aucun membre du répertoire
     * ne recouvre. Il n'est lu que lorsque {@link #mainTeacher} est absent.
     */
    @Column(name = "main_teacher_name", length = 128)
    private String mainTeacherName;

    @ManyToOne
    @ToString.Exclude
    @JoinColumn(name = "level_of_study_id", referencedColumnName = "id")
    private LevelOfStudyEntity levelOfStudy;

    @ManyToOne(optional = false)
    @ToString.Exclude
    @JoinColumn(name = "establishment_id", referencedColumnName = "id")
    private EstablishmentEntity establishment;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        SchoolClassEntity that = (SchoolClassEntity) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
