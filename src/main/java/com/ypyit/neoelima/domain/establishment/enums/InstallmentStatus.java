package com.ypyit.neoelima.domain.establishment.enums;

/**
 * État d'une tranche due par un élève.
 *
 * <p>Une tranche est indivisible en V1 : elle se règle en une fois, il n'existe donc pas d'état
 * « partiellement payée ». {@code CANCELLED} couvre l'annulation manuelle par le comptable, par
 * exemple après le départ d'un élève. {@code WRITTEN_OFF} est une dette reconnue irrécouvrable :
 * proposée par la comptabilité, validée par la direction ({@code DebtWriteOffService}). Elle n'est
 * plus réclamée, mais reste lisible comme une perte, à la différence d'une annulation.
 */
public enum InstallmentStatus {
    PENDING,
    PAID,
    CANCELLED,
    WRITTEN_OFF,
    /** Reportée sur l'année suivante : la dette continue sous une nouvelle tranche ({@code carriedTo}). */
    CARRIED_OVER
}
