package com.ypyit.neoelima.domain.establishment.entity;

import com.ypyit.neoelima.common.entity.BaseEntity;
import com.ypyit.neoelima.domain.user.entity.TranslateEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import com.ypyit.neoelima.domain.establishment.enums.EducationCycle;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.hibernate.Hibernate;

import java.io.Serial;
import java.util.Objects;

@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "level_of_study")
public class LevelOfStudyEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @Column(unique = true)
    private String code;

    /**
     * Cycle auquel le niveau appartient.
     *
     * <p>Porté par le niveau plutôt que déduit du code dans chaque écran : la règle est la même
     * partout, et la recopier côté client garantirait qu'un jour l'un range la sixième au primaire.
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private EducationCycle cycle;
    private int position;
    private String previous;
    private String next;
    @ToString.Exclude
    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "name_id", referencedColumnName = "id")
    private TranslateEntity name;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        LevelOfStudyEntity that = (LevelOfStudyEntity) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}
