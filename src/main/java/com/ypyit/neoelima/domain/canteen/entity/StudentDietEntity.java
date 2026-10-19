package com.ypyit.neoelima.domain.canteen.entity;

import com.ypyit.neoelima.domain.canteen.enums.DietKind;
import com.ypyit.neoelima.domain.establishment.entity.StudentEntity;
import com.ypyit.neoelima.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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

/** Un régime ou une allergie de l'élève, déclaré à son dossier. */
@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "student_diet")
public class StudentDietEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @ToString.Exclude
    @JoinColumn(name = "student_id", referencedColumnName = "id")
    private StudentEntity student;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DietKind kind;

    @Column(length = 128)
    private String label;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        StudentDietEntity that = (StudentDietEntity) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
