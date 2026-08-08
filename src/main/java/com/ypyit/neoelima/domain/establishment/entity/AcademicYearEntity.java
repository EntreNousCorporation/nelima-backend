package com.ypyit.neoelima.domain.establishment.entity;

import com.ypyit.neoelima.common.entity.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Année scolaire déclarée par l'établissement.
 *
 * <p>Une seule est active à la fois, et c'est le service qui le garantit : deux années actives
 * feraient répondre deux dates différentes à la question « où en est-on ? ».
 *
 * <p>Les années passées sont conservées plutôt que remplacées. Une école qui déclare 2026-2027 ne
 * doit pas perdre les bornes de 2025-2026, sur lesquelles ses reçus et ses échéances sont datés.
 */
@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "academic_year", uniqueConstraints = {
        @UniqueConstraint(name = "uk_academic_year_establishment_label",
                columnNames = {"establishment_id", "label"})
})
public class AcademicYearEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Libellé tel que l'école le nomme : « 2025-2026 ». */
    @Column(nullable = false, length = 32)
    private String label;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = false;

    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @OneToMany(mappedBy = "academicYear", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position asc")
    private List<AcademicPeriodEntity> periods = new ArrayList<>();

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @ToString.Exclude
    @JoinColumn(name = "establishment_id", referencedColumnName = "id")
    private EstablishmentEntity establishment;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        AcademicYearEntity that = (AcademicYearEntity) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
