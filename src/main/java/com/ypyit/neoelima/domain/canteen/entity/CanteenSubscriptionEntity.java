package com.ypyit.neoelima.domain.canteen.entity;

import com.ypyit.neoelima.domain.establishment.entity.AcademicYearEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentFeeEntity;
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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.hibernate.Hibernate;

import java.io.Serial;
import java.util.Objects;

/** Abonnement d'un élève à une formule, du jour de son inscription à sa fin éventuelle. */
@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "canteen_subscription")
public class CanteenSubscriptionEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @ToString.Exclude
    @JoinColumn(name = "student_id", referencedColumnName = "id")
    private StudentEntity student;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @ToString.Exclude
    @JoinColumn(name = "formula_id", referencedColumnName = "id")
    private CanteenFormulaEntity formula;

    @ManyToOne(fetch = FetchType.LAZY)
    @ToString.Exclude
    @JoinColumn(name = "academic_year_id", referencedColumnName = "id")
    private AcademicYearEntity academicYear;

    @Column(nullable = false)
    private LocalDate startDate;

    /** Facturation au mois ou au trimestre, choisie à l'inscription. */
    @Builder.Default
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "billing_period", nullable = false, length = 8)
    private com.ypyit.neoelima.domain.establishment.enums.BillingPeriod billingPeriod = com.ypyit.neoelima.domain.establishment.enums.BillingPeriod.MONTHLY;

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
        CanteenSubscriptionEntity that = (CanteenSubscriptionEntity) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
