package com.ypyit.neoelima.domain.user.entity;

import com.ypyit.neoelima.common.entity.BaseEntity;
import com.ypyit.neoelima.domain.utils.FunctionalUtils;
import jakarta.persistence.CascadeType;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.Entity;
import jakarta.persistence.Inheritance;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
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
import org.hibernate.annotations.DiscriminatorOptions;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static jakarta.persistence.InheritanceType.SINGLE_TABLE;

@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "users")
@DiscriminatorOptions(force = true)
@Inheritance(strategy = SINGLE_TABLE)
@DiscriminatorColumn(discriminatorType = DiscriminatorType.STRING, name = "user_type")
public class UserEntity extends BaseEntity implements UserDetails {

    @Serial
    private static final long serialVersionUID = 1L;
    private String firstName;
    private String lastName;
    @Builder.Default
    private boolean enabled = true;
    private Instant lastLogin;

    /**
     * Dernière ouverture du panneau de notifications.
     *
     * <p>C'est tout l'état « lu » du produit : un élément postérieur à cette date est non lu. Le
     * flux lui-même n'est pas stocké — il se recalcule des données existantes —, et lui donner une
     * table entière pour n'y écrire qu'un compteur de badge serait une seconde vérité à tenir
     * d'accord avec la première.
     */
    private Instant notificationsSeenAt;

    /**
     * Heures calmes : bornes pendant lesquelles rien ne sonne.
     *
     * <p>Elles <strong>taisent la notification, elles ne suppriment pas l'information</strong> : le
     * fil se déduit de faits datés, rien n'y est stocké, et un rappel tu à 23h y reste — il compte
     * dans la pastille au réveil. C'est ce qui permet de s'en tenir à deux colonnes plutôt qu'à une
     * file d'envois différés. Un rappel d'échéance purement supprimé, lui, deviendrait un impayé.
     *
     * <p>Nulles par défaut : personne n'est mis en silence sans l'avoir demandé. La fenêtre peut
     * franchir minuit — 22h00 → 06h00 est le cas courant, et le plus utile.
     */
    private LocalTime quietFrom;

    private LocalTime quietTo;
    private boolean locked;
    @ManyToOne
    @ToString.Exclude
    @JoinColumn(name = "role_id", referencedColumnName = "id")
    private RoleEntity role;
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinTable(
            name = "user_contacts",
            joinColumns = @JoinColumn(name = "user_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(name = "contact_id", referencedColumnName = "id")
    )
    private Set<ContactEntity> contacts = new HashSet<>();
    @ToString.Exclude
    @JoinColumn(name = "password_id", referencedColumnName = "id")
    @OneToOne(cascade = {CascadeType.ALL})
    private PasswordEntity passwordValue;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (Objects.isNull(this.getRole())) {
            return Collections.emptyList();
        }
        return FunctionalUtils
                .safelyGetStream(this.getRole().getPermissions())
                .map(func -> new SimpleGrantedAuthority(func.getCode()))
                .collect(Collectors.toList());
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !this.locked;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return Objects.nonNull(this.passwordValue) && !this.passwordValue.isExpired();
    }

    public String getPassword() {
        return FunctionalUtils.getOrNull(() -> this.passwordValue.getValue());
    }

    public String getUsername() {
        // Null plutôt qu'une exception quand le contact principal manque : cette méthode est appelée
        // pour chaque tuteur lors de la sérialisation d'un élève, et un seul co-tuteur aux données
        // abîmées faisait alors échouer toute la fratrie — 400 sur /students/mine, 500 sur /students.
        // Un compte qui s'authentifie a forcément un contact principal (il est chargé par lui) : le
        // null ne concerne que ces données héritées cassées, où taire le champ vaut mieux que rompre.
        return FunctionalUtils
                .safelyGetStream(this.getContacts())
                .filter(ContactEntity::isPrimary)
                .map(ContactEntity::getValue)
                .findFirst()
                .orElse(null);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        UserEntity that = (UserEntity) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
