package com.ypyit.neoelima.domain.establishment.dto;

import com.ypyit.neoelima.domain.establishment.enums.EnrollmentSource;
import com.ypyit.neoelima.domain.establishment.enums.EnrollmentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityEnrollmentDto {

    private String id;
    private EnrollmentStatus status;
    private EnrollmentSource source;
    private Instant requestedAt;

    private String activityId;
    private String activityName;

    private String studentId;
    private String studentFirstName;
    private String studentLastName;
    private String studentRegistrationNumber;
    private String className;

    /** Montant dû au titre de l'inscription ; nul en liste d'attente ou pour une activité gratuite. */
    private BigDecimal amountDue;

    /** Vrai dès qu'une tranche a été réglée : l'inscription ne peut plus être annulée. */
    private boolean partiallyPaid;
}
