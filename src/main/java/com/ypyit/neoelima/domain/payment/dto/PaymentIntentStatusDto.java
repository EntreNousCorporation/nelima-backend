package com.ypyit.neoelima.domain.payment.dto;

import com.ypyit.neoelima.domain.payment.enums.PaymentIntentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Où en est une tentative de paiement.
 *
 * <p>Servi à l'application pendant qu'elle attend, tunnel de l'agrégateur ouvert. Jusqu'ici elle
 * concluait sur la seule redirection d'URL : elle annonçait « paiement réussi » alors que la
 * tranche était encore due, et le webhook la soldait quelques secondes plus tard — ou pas.
 *
 * <p>{@link PaymentIntentStatus#SUCCEEDED} n'est posé que par {@code PaymentSettlementService},
 * c'est-à-dire <strong>après</strong> que la tranche est soldée et le reçu émis. Quand il apparaît,
 * il n'y a plus rien à attendre : c'est le seul signal qui autorise à affirmer quoi que ce soit à
 * une famille.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentIntentStatusDto {

    private UUID paymentIntentId;

    private PaymentIntentStatus status;

    private UUID installmentId;

    /** Somme réellement débitée, commission comprise. */
    private BigDecimal totalAmount;

    private String currency;

    /**
     * Reçu émis, s'il l'est déjà.
     *
     * <p>Permet à l'écran de succès d'offrir « Voir le reçu » sans un second aller-retour. Nul tant
     * que la tentative n'est pas réglée.
     */
    private UUID receiptId;
}
