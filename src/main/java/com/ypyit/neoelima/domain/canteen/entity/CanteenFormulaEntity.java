package com.ypyit.neoelima.domain.canteen.entity;

import com.ypyit.neoelima.domain.establishment.entity.AcademicYearEntity;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.FeeEntity;
import lombok.Builder;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
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

/**
 * Formule de cantine de l'année : ses jours, son tarif mensuel.
 *
 * <p>Le frais qui porte le tarif est créé avec elle, facultatif et hors scolarité : comme pour une
 * activité, c'est l'abonnement qui fait naître la dette, pas l'appartenance à un niveau.
 */
@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "canteen_formula")
public class CanteenFormulaEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @ToString.Exclude
    @JoinColumn(name = "establishment_id", referencedColumnName = "id")
    private EstablishmentEntity establishment;

    @ManyToOne(fetch = FetchType.LAZY)
    @ToString.Exclude
    @JoinColumn(name = "academic_year_id", referencedColumnName = "id")
    private AcademicYearEntity academicYear;

    @Column(nullable = false, length = 64)
    private String name;

    /** Jours servis, séparés par des virgules ; vide pour une formule ponctuelle. */
    @Column(length = 80)
    private String days;

    @Column(nullable = false)
    private BigDecimal monthlyPrice;

    /** Prix d'un repas pour une formule au repas (ticket) ; nul pour une formule mensuelle. */
    private BigDecimal mealPrice;

    /** Formule au repas : facturée au repas servi, et non au forfait mensuel. */
    public boolean perMeal() {
        return java.util.Objects.nonNull(this.mealPrice);
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @ToString.Exclude
    @JoinColumn(name = "fee_id", referencedColumnName = "id")
    private FeeEntity fee;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    public Set<DayOfWeek> dayOfWeeks() {
        if (Objects.isNull(this.days) || this.days.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(this.days.split(",")).map(String::trim).map(DayOfWeek::valueOf)
                .collect(Collectors.toSet());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        CanteenFormulaEntity that = (CanteenFormulaEntity) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
