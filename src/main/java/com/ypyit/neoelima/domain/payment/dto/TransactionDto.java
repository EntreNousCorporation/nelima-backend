package com.ypyit.neoelima.domain.payment.dto;

import com.ypyit.neoelima.domain.payment.enums.PaymentChannel;
import com.ypyit.neoelima.domain.payment.enums.PaymentIntentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Une tentative de paiement, telle que l'école la voit dans son rapprochement.
 *
 * <p>Les montants ne sont pas recalculés : {@code amountSchool} et {@code amountCommission} sont
 * séparés dès l'initiation du paiement. Les recomposer ici depuis un total et un taux ferait
 * diverger l'écran de ce qui a réellement été prélevé le jour où le taux change.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionDto {

    private String id;

    /** Référence pivot, au format {@code PSW-XXXXXXXXXX}. Nulle pour un encaissement au guichet. */
    private String reference;

    private Instant createdAt;
    private Instant settledAt;

    private PaymentIntentStatus status;
    private PaymentChannel channel;

    /** Agrégateur ayant traité l'opération ; nul au guichet. */
    private String providerType;

    private String studentLabel;
    private String studentRegistrationNumber;

    /** Ce qui était dû : le libellé de la tranche réglée. */
    private String installmentLabel;

    private String payerName;

    private BigDecimal amountSchool;
    private BigDecimal amountCommission;

    /**
     * Un reçu existe pour cette opération.
     *
     * <p>Une opération réussie sans reçu est ce que la maquette appelle « à réconcilier » : l'argent
     * est arrivé chez l'agrégateur mais Nelima n'a pas émis de pièce, et la comptabilité de l'école
     * est incomplète.
     */
    private boolean reconciled;

    private String receiptNumber;
}
