package com.ypyit.neoelima.domain.writeoff.dto;

import com.ypyit.neoelima.domain.writeoff.enums.WriteOffStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Une demande de passage en perte, telle que la comptabilité et la direction la lisent. */
@Getter
@Builder
public class WriteOffDto {
    private String id;
    private UUID installmentId;
    private UUID studentId;
    private UUID studentPhotoId;
    private String studentName;
    private String registrationNumber;
    /** « Scolarité — 2e tranche ». */
    private String installmentLabel;
    private LocalDate dueDate;
    private BigDecimal amount;
    private String reason;
    private WriteOffStatus status;
    private String proposedBy;
    private Instant proposedAt;
    private String decidedBy;
    private Instant decidedAt;
    private String decisionNote;
    /** L'utilisateur courant peut trancher : il valide, et ce n'est pas lui qui a proposé. */
    private boolean decidable;
}
