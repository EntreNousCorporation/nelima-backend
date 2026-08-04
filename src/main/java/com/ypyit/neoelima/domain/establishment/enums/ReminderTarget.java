package com.ypyit.neoelima.domain.establishment.enums;

/**
 * Familles visées par une campagne de relance.
 *
 * <p>Quatre cibles fermées plutôt qu'un filtre libre : une école qui compose sa propre requête finit
 * par relancer tout le monde, et c'est ainsi qu'un canal payant devient un canal ignoré.
 */
public enum ReminderTarget {

    /** Tranches échues depuis plus de trente jours. */
    LATE_30,

    /** Tranches échues depuis plus de sept jours. */
    LATE_7,

    /** Échéance à venir dans les cinq jours. */
    DUE_SOON,

    /** Toutes les tranches encore dues, échues ou non. */
    ALL_UNPAID
}
