package com.ypyit.neoelima.domain.canteen.entity;

import com.ypyit.neoelima.domain.canteen.enums.CanteenAttendanceStatus;
import com.ypyit.neoelima.domain.establishment.entity.StudentEntity;
import java.time.LocalDate;
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

/** Une exception au pointage : un abonné absent, ou un repas ponctuel servi. */
@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "canteen_attendance")
public class CanteenAttendanceEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @ToString.Exclude
    @JoinColumn(name = "student_id", referencedColumnName = "id")
    private StudentEntity student;

    @Column(nullable = false)
    private LocalDate day;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CanteenAttendanceStatus status;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        CanteenAttendanceEntity that = (CanteenAttendanceEntity) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
