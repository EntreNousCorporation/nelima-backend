package com.ypyit.neoelima.domain.establishment.entity;

import com.ypyit.neoelima.common.entity.BaseEntity;
import com.ypyit.neoelima.domain.user.entity.UserEntity;
import com.ypyit.neoelima.domain.establishment.enums.Gender;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
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
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "student", uniqueConstraints = {@UniqueConstraint(columnNames = {"registrationNumber", "establishment_id"})
})
public class StudentEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;
    private String firstName;
    private String lastName;
    private String registrationNumber;
    private LocalDate birthDay;
    private String placeOfBirth;

    /**
     * Nul tant que l'école ne l'a pas renseigné.
     *
     * <p>Des élèves étaient déjà inscrits quand la colonne est apparue : l'exiger rétroactivement
     * aurait supposé de deviner, et une donnée d'état civil devinée vaut moins que son absence.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 16)
    private Gender gender;
    @ManyToOne
    @ToString.Exclude
    @JoinColumn(name = "level_of_study_id", referencedColumnName = "id")
    private LevelOfStudyEntity levelOfStudy;
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToMany(cascade = {CascadeType.ALL})
    @JoinTable(
            name = "student_parents",
            joinColumns = @JoinColumn(name = "student_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(name = "user_id", referencedColumnName = "id")
    )
    private Set<UserEntity> parentUsers = new HashSet<>();
    /*
     * LAZY. C'est la dernière association eager de la chaîne de l'argent, et la plus délicate : elle
     * est sur le chemin d'autorisation — `CurrentUserProvider.assertCanAccessStudent` et les gardes
     * de périmètre la lisent.
     *
     * Tous ses lecteurs sont dans des services transactionnels, elle y devient donc un select de
     * plus, jamais une exception. Livrée séparément des trois pas précédents, exprès : si un
     * incident survient sur le paiement, on doit pouvoir dire lequel accuser.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @ToString.Exclude
    @JoinColumn(name = "establishment_id", referencedColumnName = "id")
    private EstablishmentEntity establishment;

    /**
     * Classe d'affectation, facultative : un élève inscrit en cours d'année attend souvent d'être
     * réparti, et le laisser sans classe vaut mieux que de le placer au hasard.
     */
    /*
     * LAZY : `schoolClass` amenait le second `level_of_study` (avec sa traduction) et le second
     * `establishment` (avec son adresse et son image). Six jointures de plus sur le chargement
     * d'une tranche, pour une donnée que seul `ReceiptIssuer` lit — une fois, à l'émission du
     * reçu, et dans une transaction.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @ToString.Exclude
    @JoinColumn(name = "school_class_id", referencedColumnName = "id")
    private SchoolClassEntity schoolClass;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        StudentEntity that = (StudentEntity) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
