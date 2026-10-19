package com.ypyit.neoelima.domain.canteen.dto;

import com.ypyit.neoelima.domain.canteen.enums.CanteenAttendanceStatus;
import com.ypyit.neoelima.domain.canteen.enums.DietKind;
import com.ypyit.neoelima.domain.establishment.enums.StudentPaymentStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Ce que l'écran Cantine reçoit. Regroupés : ce sont de simples vues, sans logique. */
public final class CanteenDtos {

    private CanteenDtos() {
    }

    @Getter
    @Builder
    public static class Formula {
        private String id;
        private String name;
        private List<DayOfWeek> days;
        private BigDecimal monthlyPrice;
        /** Prix d'un repas pour une formule au repas ; nul sinon. */
        private BigDecimal mealPrice;
        private boolean active;
        private long subscribers;
    }

    @Getter
    @Builder
    public static class Overview {
        private LocalDate day;
        private long subscribers;
        /** Part de l'effectif de l'année inscrite à la cantine, en pour cent. */
        private int shareOfEnrollment;
        private long expectedToday;
        private long servedToday;
        private long absentToday;
        private long studentsWithDiet;
        private long allergies;
        private BigDecimal unpaidAmount;
        private long unpaidStudents;
        private LocalDate nextDueDate;
        private List<DietCount> diets;
    }

    @Getter
    @Builder
    public static class DietCount {
        private DietKind kind;
        private long count;
    }

    @Getter
    @Builder
    public static class Subscriber {
        private String subscriptionId;
        private com.ypyit.neoelima.domain.establishment.enums.BillingPeriod billingPeriod;
        private UUID studentId;
        private String studentName;
        private String registrationNumber;
        private UUID photoId;
        private String className;
        private String formulaId;
        private String formulaName;
        private LocalDate startDate;
        private LocalDate endDate;
        private List<Diet> diets;
        private StudentPaymentStatus paymentStatus;
        /** Ce jour-là : servi, absent, ou pas attendu (jour hors formule, ponctuel non servi). */
        private CanteenAttendanceStatus today;
        private boolean expectedToday;
    }

    @Getter
    @Builder
    public static class Diet {
        private DietKind kind;
        private String label;
    }

    @Getter
    @Builder
    public static class MenuDay {
        private LocalDate day;
        private String starter;
        private String mainCourse;
        private String dessert;
        private String vegetarianAlternative;
        private List<String> allergens;
        private boolean published;
        private boolean closed;
    }

    /** Fiche cuisine : pour un régime, les élèves attendus ce jour-là. */
    @Getter
    @Builder
    public static class KitchenLine {
        private DietKind kind;
        private List<KitchenStudent> students;
    }

    @Getter
    @Builder
    public static class KitchenStudent {
        private String name;
        private String className;
        private String label;
    }
}
