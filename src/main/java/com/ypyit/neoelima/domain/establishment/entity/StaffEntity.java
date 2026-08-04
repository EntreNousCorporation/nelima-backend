package com.ypyit.neoelima.domain.establishment.entity;

import com.ypyit.neoelima.common.entity.BaseEntity;
import com.ypyit.neoelima.domain.establishment.enums.ContractType;
import com.ypyit.neoelima.domain.establishment.enums.StaffRole;
import com.ypyit.neoelima.domain.user.entity.UserEntity;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Membre du personnel de l'établissement.
 *
 * <p>Distinct du compte de connexion : la plupart des enseignants n'ont pas accès au portail, et
 * confondre les deux obligerait à créer un compte à chaque embauche. L'accès se donne ensuite, et
 * le lien vers l'utilisateur reste facultatif.
 */
@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "staff")
public class StaffEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @Column(nullable = false, length = 128)
    private String firstName;

    @Column(nullable = false, length = 128)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private StaffRole role;

    /** Intitulé du poste ou matière enseignée, tel que l'école le nomme. */
    @Column(length = 128)
    private String jobTitle;

    @Column(length = 64)
    private String phone;

    @Column(length = 128)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private ContractType contractType;

    /** Salaire mensuel brut. Facultatif : toutes les écoles ne le confient pas à la plateforme. */
    @Column(precision = 38, scale = 2)
    private BigDecimal monthlySalary;

    /** Heures assurées par semaine, pour la charge des enseignants. */
    private Integer weeklyHours;

    private LocalDate hiredAt;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    @ManyToOne(optional = false)
    @ToString.Exclude
    @JoinColumn(name = "establishment_id", referencedColumnName = "id")
    private EstablishmentEntity establishment;

    /**
     * Compte de connexion, quand un accès au portail lui a été ouvert.
     *
     * <p>Facultatif à dessein : c'est ce qui distingue le répertoire du personnel de la liste des
     * comptes, et permet d'inscrire un enseignant sans lui ouvrir la comptabilité de l'école.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @ToString.Exclude
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    private UserEntity user;

    /** Classes où il intervient. Le titulariat, lui, se lit depuis la classe. */
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToMany(cascade = {CascadeType.MERGE, CascadeType.PERSIST})
    @JoinTable(
            name = "staff_class",
            joinColumns = @JoinColumn(name = "staff_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(name = "school_class_id", referencedColumnName = "id")
    )
    private Set<SchoolClassEntity> classes = new HashSet<>();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        StaffEntity that = (StaffEntity) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
