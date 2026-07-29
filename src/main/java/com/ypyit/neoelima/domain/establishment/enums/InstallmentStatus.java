package com.ypyit.neoelima.domain.establishment.enums;

/**
 * État d'une tranche due par un élève.
 *
 * <p>Une tranche est indivisible en V1 : elle se règle en une fois, il n'existe donc pas d'état
 * « partiellement payée ». {@code CANCELLED} couvre l'annulation manuelle par le comptable, par
 * exemple après le départ d'un élève.
 */
public enum InstallmentStatus {
    PENDING,
    PAID,
    CANCELLED
}
