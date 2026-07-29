package com.ypyit.neoelima.domain.establishment.entity;

import com.ypyit.neoelima.common.entity.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderBy;
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
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "fee", uniqueConstraints = {@UniqueConstraint(columnNames = {"name", "establishment_id"})
})
public class FeeEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;
    private String name;
    private BigDecimal price;
    private boolean optional;
    private boolean academical;
    @ManyToOne
    @ToString.Exclude
    @JoinColumn(name = "establishment_id", referencedColumnName = "id")
    private EstablishmentEntity establishment;
    @Builder.Default
    @ToString.Exclude
    @OrderBy("position")
    @EqualsAndHashCode.Exclude
    @ManyToMany(cascade = CascadeType.ALL)
    @JoinTable(
            name = "fee_level_of_studies",
            joinColumns = @JoinColumn(name = "fee_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(name = "level_of_study_id", referencedColumnName = "id")
    )
    private Set<LevelOfStudyEntity> levelOfStudies = new HashSet<>();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        FeeEntity that = (FeeEntity) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
