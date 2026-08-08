package com.ypyit.neoelima.domain.subscription.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionInvoiceDto {

    private String id;
    private String number;
    private String establishmentId;
    private String establishmentName;

    private String plan;
    private String planLabel;
    private BigDecimal amount;

    private LocalDate periodStart;
    private LocalDate periodEnd;
    private Instant issuedAt;
    private LocalDate dueAt;
    private Instant paidAt;
    private String paymentMethod;
    private String paymentReference;

    private boolean cancelled;
    private String cancellationReason;

    /**
     * État servi calculé, jamais stocké : `PAID`, `LATE`, `ISSUED` ou `CANCELLED`.
     *
     * <p>Le déduire à la lecture évite d'avoir à faire passer un travail programmé chaque nuit pour
     * basculer les factures échues — et évite surtout qu'une facture reste affichée « à jour » le
     * lendemain de son échéance parce que le travail n'a pas tourné.
     */
    private String status;
}
