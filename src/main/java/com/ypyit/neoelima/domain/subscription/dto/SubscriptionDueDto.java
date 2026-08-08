package com.ypyit.neoelima.domain.subscription.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Une école dont la période d'abonnement est échue et qui n'a pas encore sa facture.
 *
 * <p>La liste est proposée à YPYit, qui émet. Aucun automate n'émet à sa place : une facture qu'on
 * produit sans la regarder est une dette que personne n'a vérifiée.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionDueDto {

    private String establishmentId;
    private String establishmentName;

    private String plan;
    private String planLabel;

    /** Tarif courant de la formule. Il sera figé sur la facture au moment de l'émission. */
    private BigDecimal amount;

    private LocalDate periodStart;
    private LocalDate periodEnd;

    private long studentCount;

    /**
     * Formule que l'effectif appellerait aujourd'hui, quand elle diffère de celle qui est souscrite.
     *
     * <p>Nulle quand les deux coïncident. C'est un signalement, pas une correction : changer la
     * formule d'office reviendrait à modifier un contrat sans que personne ne l'ait décidé.
     */
    private String suggestedPlan;
}
