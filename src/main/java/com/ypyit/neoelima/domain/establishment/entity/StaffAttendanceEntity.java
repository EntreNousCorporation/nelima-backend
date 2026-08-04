package com.ypyit.neoelima.domain.establishment.entity;

import com.ypyit.neoelima.common.entity.BaseEntity;
import com.ypyit.neoelima.domain.establishment.enums.AttendanceStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.hibernate.Hibernate;

import java.io.Serial;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Pointage d'un membre du personnel sur une journée.
 *
 * <p>Une ligne par personne et par jour, garantie par la contrainte d'unicité : pointer deux fois
 * la même personne le même matin produirait deux vérités contradictoires dans le même tableau.
 */
@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "staff_attendance", uniqueConstraints = {
        @UniqueConstraint(name = "uk_staff_attendance_staff_day", columnNames = {"staff_id", "day"})
})
public class StaffAttendanceEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @Column(name = "day", nullable = false)
    private LocalDate day;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AttendanceStatus status;

    @Column(length = 255)
    private String note;

    @ManyToOne(optional = false)
    @ToString.Exclude
    @JoinColumn(name = "staff_id", referencedColumnName = "id")
    private StaffEntity staff;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        StaffAttendanceEntity that = (StaffAttendanceEntity) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
