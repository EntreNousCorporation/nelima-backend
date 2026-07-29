package com.ypyit.neoelima.domain.payment.enums;

/**
 * État d'une tentative de paiement côté Nelima.
 *
 * <p>Volontairement distinct du statut PaySwitch : PaySwitch décrit la vie de la transaction chez
 * l'agrégateur, cette énumération décrit ce que Nelima en a fait. Une transaction réussie chez
 * l'agrégateur mais dont l'application n'a pas encore soldé la tranche reste un cas à traiter, et
 * il doit rester visible.
 */
public enum PaymentIntentStatus {
    /** Créée, en attente du webhook de l'agrégateur. */
    PENDING,
    /** Encaissement confirmé et tranche soldée. */
    SUCCEEDED,
    FAILED,
    CANCELLED
}
