package com.ypyit.neoelima.domain.establishment.entity;

import com.ypyit.neoelima.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.Hibernate;

import java.io.Serial;
import java.util.Objects;

/**
 * Groupe scolaire à plusieurs sites. Chaque site est un établissement ({@code establishment.school_group_id}) ;
 * le groupe ne porte aucune donnée scolaire, seulement le lien entre ses sites.
 */
@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "school_group")
public class SchoolGroupEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @Column(nullable = false, length = 120, unique = true)
    private String name;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        SchoolGroupEntity that = (SchoolGroupEntity) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
