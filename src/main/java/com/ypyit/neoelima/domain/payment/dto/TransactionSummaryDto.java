package com.ypyit.neoelima.domain.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Les quatre chiffres que l'école lit avant d'ouvrir la liste. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionSummaryDto {

    /** Net encaissé sur la période, commissions déduites. */
    private BigDecimal collectedNet;

    private long transactionCount;

    /** Opérations réussies sans reçu émis : la comptabilité de l'école est incomplète. */
    private long toReconcile;

    /** Paiements initiés dont l'agrégateur n'a pas encore confirmé le sort. */
    private long awaitingProvider;

    /** Commissions prélevées, à la charge du payeur. */
    private BigDecimal commissionCollected;
}
