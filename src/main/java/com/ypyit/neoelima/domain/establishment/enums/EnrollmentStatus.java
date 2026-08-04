package com.ypyit.neoelima.domain.establishment.enums;

/**
 * État d'une inscription à une activité.
 *
 * <p>La liste d'attente est un état de l'inscription et non une autre table : c'est la même demande
 * qui attend puis aboutit, et la scinder en deux ferait perdre l'ordre d'arrivée au moment précis
 * où il sert.
 */
public enum EnrollmentStatus {

    ENROLLED,

    /** En attente d'une place. Aucune dette n'est créée tant qu'elle n'est pas obtenue. */
    WAITLISTED,

    CANCELLED
}
