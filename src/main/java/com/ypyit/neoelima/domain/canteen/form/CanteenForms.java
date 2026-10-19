package com.ypyit.neoelima.domain.canteen.form;

import com.ypyit.neoelima.domain.canteen.enums.CanteenAttendanceStatus;
import com.ypyit.neoelima.domain.canteen.enums.DietKind;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class CanteenForms {

    private CanteenForms() {
    }

    @Getter
    @Setter
    public static class Formula {
        @NotBlank
        @Size(max = 64)
        private String name;
        /** Vide : formule ponctuelle, servie à la demande. */
        private List<DayOfWeek> days = new ArrayList<>();
        /** Forfait mensuel ; absent pour une formule au repas. */
        @DecimalMin("0")
        private BigDecimal monthlyPrice;
        /** Prix d'un repas : la formule est alors au repas (ticket), sans jour fixe. */
        @DecimalMin("0")
        private BigDecimal mealPrice;
        private boolean active = true;
    }

    @Getter
    @Setter
    public static class Subscription {
        @NotNull
        private UUID studentId;
        @NotNull
        private UUID formulaId;
        /** Premier jour de cantine ; absent, aujourd'hui. */
        private LocalDate startDate;
        /** Prévenir la famille : menu de la semaine et montant mensuel. */
        private boolean notifyFamily;
        /** Au mois (par défaut) ou au trimestre ; sans objet pour une formule au repas. */
        private com.ypyit.neoelima.domain.establishment.enums.BillingPeriod billingPeriod;
    }

    @Getter
    @Setter
    public static class End {
        /** Dernier jour de cantine ; absent, aujourd'hui. Les mois suivants ne sont plus dus. */
        private LocalDate endDate;
    }

    @Getter
    @Setter
    public static class Attendance {
        @NotNull
        private UUID studentId;
        @NotNull
        private LocalDate day;
        /** Nul revient à la règle : servi les jours de la formule. */
        private CanteenAttendanceStatus status;
    }

    @Getter
    @Setter
    public static class MenuDay {
        @NotNull
        private LocalDate day;
        @Size(max = 128)
        private String starter;
        @Size(max = 128)
        private String mainCourse;
        @Size(max = 128)
        private String dessert;
        @Size(max = 128)
        private String vegetarianAlternative;
        private List<String> allergens = new ArrayList<>();
        /** Pas de service ce jour-là. */
        private boolean closed;
    }

    @Getter
    @Setter
    public static class Menu {
        @Valid
        private List<MenuDay> days = new ArrayList<>();
    }

    @Getter
    @Setter
    public static class Diet {
        @NotNull
        private DietKind kind;
        @Size(max = 128)
        private String label;
    }
}
