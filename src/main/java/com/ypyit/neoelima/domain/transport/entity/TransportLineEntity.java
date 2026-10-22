package com.ypyit.neoelima.domain.transport.entity;

import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.AcademicYearEntity;
import com.ypyit.neoelima.domain.establishment.entity.FeeEntity;
import com.ypyit.neoelima.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

/** Ligne de car de l'année : son car, son chauffeur, ses horaires et son tarif mensuel. */
@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "transport_line")
public class TransportLineEntity extends BaseEntity {

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

    /** Véhicule affecté ; immatriculation et places en sont recopiées sur la ligne. */
    @ManyToOne(fetch = FetchType.LAZY)
    @ToString.Exclude
    @JoinColumn(name = "vehicle_id", referencedColumnName = "id")
    private TransportVehicleEntity vehicle;

    @Column(length = 16)
    private String vehiclePlate;

    @Column(nullable = false)
    private Integer capacity;

    @ManyToOne(fetch = FetchType.LAZY)
    @ToString.Exclude
    @JoinColumn(name = "driver_id", referencedColumnName = "id")
    private TransportDriverEntity driver;

    private LocalTime morningDeparture;

    private LocalTime eveningDeparture;

    /** Jours de circulation, « MONDAY,TUESDAY,… ». */
    @Builder.Default
    @Column(nullable = false, length = 80)
    private String days = "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY";

    @Column(length = 96)
    private String attendant;

    /** Mise en service : les inscriptions commencent au plus tôt ce jour-là. */
    private LocalDate startDate;

    @Column(nullable = false)
    private BigDecimal monthlyPrice;

    @ManyToOne(fetch = FetchType.LAZY)
    @ToString.Exclude
    @JoinColumn(name = "fee_id", referencedColumnName = "id")
    private FeeEntity fee;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        TransportLineEntity that = (TransportLineEntity) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
