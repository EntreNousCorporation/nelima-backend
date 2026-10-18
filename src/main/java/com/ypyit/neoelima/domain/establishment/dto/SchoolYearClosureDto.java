package com.ypyit.neoelima.domain.establishment.dto;

import com.ypyit.neoelima.domain.establishment.enums.SchoolYearStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Où en est la clôture d'une année dans une école : ce qui reste à encaisser, et si l'on peut
 * clôturer.
 */
@Getter
@Builder
public class SchoolYearClosureDto {
    private String academicYearId;
    private String label;
    private SchoolYearStatus status;
    private Instant closedAt;
    private String closedBy;
    /** L'année en cours ne se clôture pas : l'école y travaille encore. */
    private boolean current;
    private boolean closable;
    /** Pourquoi on ne peut pas clôturer, en une phrase ; nul si on le peut. */
    private String blocker;
    private long pendingCount;
    private BigDecimal pendingAmount;
    /** Paiements en ligne encore en attente de confirmation sur des tranches de l'année. */
    private long paymentsInProgress;
    /** Les élèves qui doivent encore, les plus gros soldes d'abord. */
    private List<Outstanding> outstanding;

    @Getter
    @Builder
    public static class SchoolState {
        private String establishmentId;
        private String name;
        private SchoolYearStatus status;
        private Instant closedAt;
        private String closedBy;
    }

    @Getter
    @Builder
    public static class Outstanding {
        private String studentId;
        private String studentName;
        private String registrationNumber;
        private long installmentCount;
        private BigDecimal amount;
    }
}
