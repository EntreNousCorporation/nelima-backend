package com.ypyit.neoelima.domain.establishment.entity;

import com.ypyit.neoelima.common.entity.BaseEntity;
import com.ypyit.neoelima.domain.transverse.entity.FileMediaEntity;
import com.ypyit.neoelima.domain.user.entity.AddressEntity;
import com.ypyit.neoelima.domain.user.entity.ContactEntity;
import com.ypyit.neoelima.domain.user.entity.PermissionEntity;
import com.ypyit.neoelima.domain.user.entity.UserEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
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
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "establishment")
public class EstablishmentEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;
    @Column(unique = true)
    private String name;

    /** Sigle affiché là où le nom complet ne tient pas : reçus, SMS, en-têtes. */
    @Column(length = 32)
    private String shortName;

    /**
     * Numéro d'agrément du ministère, au format {@code MENA/DRENA-…}.
     *
     * <p>Il figure sur les documents officiels de l'école ; le stocker évite qu'un secrétariat le
     * recopie de mémoire sur chaque pièce.
     */
    @Column(length = 64)
    private String accreditationNumber;

    /**
     * Formule d'abonnement souscrite auprès de YPYit.
     *
     * <p>Le code de la formule, et non la formule elle-même : une facture émise sous un palier qui
     * a depuis changé de nom doit rester lisible, et la grille vit en base.
     *
     * <p>Stockée et non déduite de l'effectif : le palier de tête se négocie sur devis, et une
     * école peut obtenir un tarif consenti. L'effectif ne fait que proposer.
     *
     * <p>Nulle pour une école qui n'a pas encore souscrit — l'onboarding la crée avant que le
     * contrat ne soit signé.
     */
    @Column(name = "subscription_plan", length = 32)
    private String subscriptionPlan;

    /** Date de souscription. C'est elle qui fixe les périodes de facturation, de douze mois. */
    private LocalDate subscribedAt;

    private String webSite;
    private String bucketName;
    private boolean isPrimary;
    private boolean active;
    @ToString.Exclude
    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "address_id", referencedColumnName = "id")
    private AddressEntity address;

    /**
     * Ville, pour situer l'école dans une liste.
     *
     * <p>Séparée de l'adresse, qui est un libellé postal complet : la découper donnerait une ville
     * fausse dès qu'une saisie ne suit pas la même forme.
     */
    @Column(length = 120)
    private String city;
    @ManyToOne
    @ToString.Exclude
    @JoinColumn(name = "principal_id", referencedColumnName = "id")
    private UserEntity principal;
    @ManyToOne
    @ToString.Exclude
    @JoinColumn(name = "parent_id", referencedColumnName = "id")
    private EstablishmentEntity parent;
    @ToString.Exclude
    @JoinColumn(name = "file_id", referencedColumnName = "id")
    @OneToOne(cascade = {CascadeType.ALL})
    private FileMediaEntity coverImage;
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinTable(
            name = "establishment_contacts",
            joinColumns = @JoinColumn(name = "establishment_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(name = "contact_id", referencedColumnName = "id")
    )
    private Set<ContactEntity> contacts = new HashSet<>();
    @Builder.Default
    @ToString.Exclude
    @OrderBy("position")
    @EqualsAndHashCode.Exclude
    @ManyToMany(cascade = CascadeType.ALL)
    @JoinTable(
            name = "establishment_level_of_studies",
            joinColumns = @JoinColumn(name = "establishment_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(name = "level_of_study_id", referencedColumnName = "id")
    )
    private Set<LevelOfStudyEntity> levelOfStudies = new HashSet<>();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        PermissionEntity that = (PermissionEntity) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
