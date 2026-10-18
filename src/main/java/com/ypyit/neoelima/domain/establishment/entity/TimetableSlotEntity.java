package com.ypyit.neoelima.domain.establishment.entity;

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
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Objects;

/**
 * Créneau de l'emploi du temps d'une classe : un jour de la semaine type, une plage horaire, une
 * matière.
 *
 * <p>La matière est un libellé et non une référence : aucun référentiel de matières n'existe, et
 * les écoles nomment les leurs à leur façon (« EPS », « Éducation physique »). L'enseignant, lui,
 * est une référence au personnel — c'est ce qui permet de refuser qu'il soit à deux endroits à
 * la fois.
 */
@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "timetable_slot")
public class TimetableSlotEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @ToString.Exclude
    @JoinColumn(name = "school_class_id", referencedColumnName = "id")
    private SchoolClassEntity schoolClass;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DayOfWeek dayOfWeek;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    @Column(nullable = false, length = 96)
    private String subject;

    @ManyToOne(fetch = FetchType.LAZY)
    @ToString.Exclude
    @JoinColumn(name = "teacher_id", referencedColumnName = "id")
    private StaffEntity teacher;

    /** Salle du créneau quand elle diffère de celle de la classe : laboratoire, terrain. */
    @Column(length = 64)
    private String room;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        TimetableSlotEntity that = (TimetableSlotEntity) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
