package com.ypyit.neoelima.domain.transport.form;

import com.ypyit.neoelima.domain.transport.enums.LineStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class TransportForms {

    private TransportForms() {
    }

    @Getter
    @Setter
    public static class Driver {
        @NotBlank
        @Size(max = 64)
        private String firstName;
        @NotBlank
        @Size(max = 64)
        private String lastName;
        @Size(max = 32)
        private String phone;
        @Size(max = 2)
        private String licenseCategory;
        private LocalDate licenseExpiry;
        /** LICENSE, ID, RECORD, MEDICAL. */
        private List<String> documents = new ArrayList<>();
        private boolean active = true;
    }

    @Getter
    @Setter
    public static class Vehicle {
        @NotBlank
        @Size(min = 6, max = 16)
        private String plate;
        private com.ypyit.neoelima.domain.transport.enums.VehicleKind kind;
        @Size(max = 64)
        private String model;
        @NotNull
        @Min(1)
        @Max(120)
        private Integer capacity;
        @NotNull
        private LocalDate insuranceExpiry;
        private LocalDate technicalVisit;
        private boolean active = true;
    }

    @Getter
    @Setter
    public static class Stop {
        /** Présent pour un arrêt existant : ses abonnés le gardent. */
        private UUID id;
        @NotBlank
        @Size(max = 96)
        private String name;
        private LocalTime pickupTime;
        private Double latitude;
        private Double longitude;
    }

    @Getter
    @Setter
    public static class Line {
        @NotBlank
        @Size(max = 64)
        private String name;
        /** Véhicule du parc ; à défaut, immatriculation et places saisies à la main. */
        private UUID vehicleId;
        @Size(max = 16)
        private String vehiclePlate;
        @Min(1)
        @Max(120)
        private Integer capacity;
        private UUID driverId;
        private LocalTime morningDeparture;
        private LocalTime eveningDeparture;
        private List<java.time.DayOfWeek> days = new ArrayList<>();
        @Size(max = 96)
        private String attendant;
        private LocalDate startDate;
        /** À la création : prévenir les familles de l'école que la ligne ouvre. */
        private boolean announce;
        @NotNull
        @DecimalMin("0")
        private BigDecimal monthlyPrice;
        private boolean active = true;
        @Valid
        private List<Stop> stops = new ArrayList<>();
    }

    @Getter
    @Setter
    public static class Subscription {
        @NotNull
        private UUID studentId;
        @NotNull
        private UUID lineId;
        private UUID stopId;
        private LocalDate startDate;
        /** Au mois (par défaut) ou au trimestre. */
        private com.ypyit.neoelima.domain.establishment.enums.BillingPeriod billingPeriod;
    }

    @Getter
    @Setter
    public static class End {
        private LocalDate endDate;
    }

    @Getter
    @Setter
    public static class Status {
        @NotNull
        private LineStatus status;
        @Min(0)
        @Max(600)
        private Integer delayMinutes;
        @Size(max = 255)
        private String note;
        /** Prévenir les familles de la ligne ; d'office pour un retard ou un incident. */
        private Boolean notifyFamilies;
    }
}
