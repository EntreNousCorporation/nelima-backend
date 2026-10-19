package com.ypyit.neoelima.domain.transport.dto;

import com.ypyit.neoelima.domain.establishment.enums.StudentPaymentStatus;
import com.ypyit.neoelima.domain.transport.enums.LineStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** Ce que l'écran Transport reçoit. */
public final class TransportDtos {

    private TransportDtos() {
    }

    @Getter
    @Builder
    public static class Driver {
        private String id;
        private String firstName;
        private String lastName;
        private String phone;
        private String licenseCategory;
        private LocalDate licenseExpiry;
        private List<String> documents;
        private boolean active;
        /** Téléphone, permis en cours de validité et les quatre pièces fournies. */
        private boolean dossierComplete;
        private List<String> lines;
    }

    @Getter
    @Builder
    public static class Stop {
        private String id;
        private int position;
        private String name;
        private LocalTime pickupTime;
        private Double latitude;
        private Double longitude;
        private long riders;
    }

    @Getter
    @Builder
    public static class Line {
        private String id;
        private String name;
        /** « Riviera 3 → Palmeraie → École » : du premier au dernier arrêt. */
        private String itinerary;
        private String vehiclePlate;
        private int capacity;
        private String driverId;
        private String driverName;
        private String driverPhone;
        private LocalTime morningDeparture;
        private LocalTime eveningDeparture;
        private List<String> days;
        private String attendant;
        private LocalDate startDate;
        private BigDecimal monthlyPrice;
        private boolean active;
        private long riders;
        private List<Stop> stops;
        private LineStatus status;
        private Integer delayMinutes;
        private String note;
    }

    @Getter
    @Builder
    public static class Overview {
        private long riders;
        private int shareOfEnrollment;
        private long linesInService;
        private long arrivedToday;
        private long seats;
        private long seatsFree;
        private int fillRate;
        private BigDecimal unpaidAmount;
        private long unpaidStudents;
        /** Lignes en retard ou en incident aujourd'hui. */
        private List<Line> alerts;
    }

    @Getter
    @Builder
    public static class Rider {
        private String subscriptionId;
        private com.ypyit.neoelima.domain.establishment.enums.BillingPeriod billingPeriod;
        private UUID studentId;
        private String studentName;
        private String registrationNumber;
        private UUID photoId;
        private String className;
        private String lineId;
        private String lineName;
        private String stopId;
        private String stopName;
        private LocalTime pickupTime;
        private LocalDate startDate;
        private LocalDate endDate;
        private StudentPaymentStatus paymentStatus;
    }

    /** Pour l'application des familles : la ligne de l'enfant et où elle en est. */
    @Getter
    @Builder
    public static class ChildRide {
        private UUID studentId;
        private String studentName;
        private String lineName;
        private String stopName;
        private LocalTime pickupTime;
        private LocalTime eveningDeparture;
        private String driverName;
        private String driverPhone;
        private LineStatus status;
        private Integer delayMinutes;
    }
}
