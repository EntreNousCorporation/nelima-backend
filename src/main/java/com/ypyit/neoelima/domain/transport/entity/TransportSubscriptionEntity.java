package com.ypyit.neoelima.domain.transport.entity;

import com.ypyit.neoelima.domain.establishment.entity.AcademicYearEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentFeeEntity;
import com.ypyit.neoelima.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import java.time.LocalDate;
import java.util.Objects;

/** Abonnement d'un élève à une ligne, montant à un arrêt. */
@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "transport_subscription")
public class TransportSubscriptionEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @ToString.Exclude
    @JoinColumn(name = "student_id", referencedColumnName = "id")
    private StudentEntity student;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @ToString.Exclude
    @JoinColumn(name = "line_id", referencedColumnName = "id")
    private TransportLineEntity line;

    @ManyToOne(fetch = FetchType.LAZY)
    @ToString.Exclude
    @JoinColumn(name = "stop_id", referencedColumnName = "id")
    private TransportStopEntity stop;

    @ManyToOne(fetch = FetchType.LAZY)
    @ToString.Exclude
    @JoinColumn(name = "academic_year_id", referencedColumnName = "id")
    private AcademicYearEntity academicYear;

    @Column(nullable = false)
    private LocalDate startDate;

    private LocalDate endDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @ToString.Exclude
    @JoinColumn(name = "student_fee_id", referencedColumnName = "id")
    private StudentFeeEntity studentFee;

    public boolean activeOn(LocalDate day) {
        return !day.isBefore(this.startDate) && (Objects.isNull(this.endDate) || !day.isAfter(this.endDate));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        TransportSubscriptionEntity that = (TransportSubscriptionEntity) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
