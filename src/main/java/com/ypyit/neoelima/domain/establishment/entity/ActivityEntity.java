package com.ypyit.neoelima.domain.establishment.entity;

import com.ypyit.neoelima.common.entity.BaseEntity;
import com.ypyit.neoelima.domain.establishment.enums.ActivityKind;
import com.ypyit.neoelima.domain.establishment.enums.ActivityStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.hibernate.Hibernate;

import java.io.Serial;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Activité extra-scolaire proposée par l'établissement.
 *
 * <p>Facultative, à la différence de la scolarité : la dette naît de l'inscription et d'elle seule.
 * C'est ce qui interdit de la traiter comme un frais ordinaire, dont le seul ciblage possible est
 * le niveau scolaire et qui facture tous les élèves de ce niveau.
 */
@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "activity", uniqueConstraints = {
        @UniqueConstraint(name = "uk_activity_establishment_name", columnNames = {"establishment_id", "name"})
})
public class ActivityEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @Column(nullable = false, length = 128)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ActivityKind kind;

    @Column(length = 128)
    private String place;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private DayOfWeek dayOfWeek;

    private LocalTime startTime;

    private LocalTime endTime;

    /** Période couverte, telle que l'école la nomme : « Année complète », « 2e trimestre ». */
    @Column(length = 64)
    private String periodLabel;

    /**
     * Nombre de places.
     *
     * <p>Contraignante, contrairement à la capacité d'une classe : au-delà, l'inscription part en
     * liste d'attente. Un cours de judo a le nombre de tapis qu'il a.
     */
    @Column(nullable = false)
    private Integer capacity;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ActivityStatus status = ActivityStatus.DRAFT;

    /**
     * Encadrant, pris dans le répertoire du personnel.
     *
     * <p>Facultatif : une activité peut être ouverte avant qu'on ait désigné qui l'anime. Un
     * intervenant extérieur se saisit comme membre du personnel en contrat vacataire, plutôt que
     * comme un nom libre qui rouvrirait la double source de vérité qu'on vient de refermer sur le
     * titulaire de classe.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @ToString.Exclude
    @JoinColumn(name = "coach_id", referencedColumnName = "id")
    private StaffEntity coach;

    /**
     * Ouverte à tout l'établissement.
     *
     * <p>Un indicateur plutôt que l'énumération de toutes les classes : sans lui, ouvrir une
     * activité à l'école entière obligerait à rattacher chaque classe créée par la suite.
     */
    @Builder.Default
    @Column(nullable = false)
    private boolean openToAll = false;

    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToMany(cascade = {CascadeType.MERGE, CascadeType.PERSIST})
    @JoinTable(
            name = "activity_class",
            joinColumns = @JoinColumn(name = "activity_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(name = "school_class_id", referencedColumnName = "id")
    )
    private Set<SchoolClassEntity> eligibleClasses = new HashSet<>();

    /**
     * Frais qui porte le tarif, nul pour une activité gratuite.
     *
     * <p>Créé sans niveau ciblé et marqué facultatif : c'est l'inscription qui produit la dette.
     * Le passer par le service des frais le facturerait à tous les élèves d'un niveau.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @ToString.Exclude
    @JoinColumn(name = "fee_id", referencedColumnName = "id")
    private FeeEntity fee;

    @ManyToOne(optional = false)
    @ToString.Exclude
    @JoinColumn(name = "establishment_id", referencedColumnName = "id")
    private EstablishmentEntity establishment;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        ActivityEntity that = (ActivityEntity) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
