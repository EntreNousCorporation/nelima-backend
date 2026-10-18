package com.ypyit.neoelima.domain.establishment.enums;

/**
 * Où en est la famille d'un élève, vu de la caisse.
 *
 * <p>Trois états exclusifs, dans cet ordre de gravité : une tranche échue fait l'élève en retard,
 * quoi qu'il ait payé par ailleurs ; sinon, avoir déjà réglé une partie d'un solde restant en fait
 * un acompte ; sinon il est à jour — rien d'échu, que rien n'ait été payé ou que tout l'ait été.
 */
public enum StudentPaymentStatus {
    UP_TO_DATE,
    DEPOSIT,
    OVERDUE
}
