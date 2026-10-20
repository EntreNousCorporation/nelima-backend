package com.ypyit.neoelima.domain.user.entity;

import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.user.enums.UserType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.hibernate.Hibernate;

import java.util.Objects;

@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@DiscriminatorValue(UserType.ESTABLISHMENT_USER)
public class EstablishmentUserEntity extends UserEntity {

    private static final long serialVersionUID = 1L;
    @ManyToOne
    @ToString.Exclude
    @JoinColumn(name = "establishment_id", referencedColumnName = "id")
    private EstablishmentEntity establishment;

    /** Direction de groupe : peut afficher n'importe quel site du groupe de son établissement. */
    @jakarta.persistence.Column(name = "group_wide", nullable = false)
    private boolean groupWide;

    /** Site affiché par une direction de groupe ; nul : son propre établissement. */
    @ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
    @ToString.Exclude
    @JoinColumn(name = "consulted_establishment_id", referencedColumnName = "id")
    private EstablishmentEntity consultedEstablishment;

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
