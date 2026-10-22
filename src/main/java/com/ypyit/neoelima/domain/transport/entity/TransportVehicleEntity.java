package com.ypyit.neoelima.domain.transport.entity;

import com.ypyit.neoelima.common.entity.BaseEntity;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.transport.enums.VehicleKind;
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
import java.time.LocalDate;
import java.util.Objects;

/**
 * Véhicule du parc de l'école. Il existe d'une année sur l'autre ; une ligne l'affecte.
 *
 * <p>L'assurance et la visite technique conditionnent le droit de rouler : la direction est
 * prévenue avant leur échéance.
 */
@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "transport_vehicle")
public class TransportVehicleEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @ToString.Exclude
    @JoinColumn(name = "establishment_id", referencedColumnName = "id")
    private EstablishmentEntity establishment;

    /** Immatriculation, en majuscules : « AB 1234 CI ». */
    @Column(nullable = false, length = 16)
    private String plate;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private VehicleKind kind = VehicleKind.CAR;

    @Column(length = 64)
    private String model;

    @Column(nullable = false)
    private Integer capacity;

    private LocalDate insuranceExpiry;

    private LocalDate technicalVisit;

    /** Échéance d'assurance déjà signalée à la direction. */
    private LocalDate insuranceAlertedFor;

    /** Échéance de visite technique déjà signalée à la direction. */
    private LocalDate technicalVisitAlertedFor;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        TransportVehicleEntity that = (TransportVehicleEntity) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
