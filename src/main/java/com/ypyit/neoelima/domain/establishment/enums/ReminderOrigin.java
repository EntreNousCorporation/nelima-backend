package com.ypyit.neoelima.domain.establishment.enums;

/**
 * Ce qui a déclenché un rappel.
 *
 * <p>Distinguer les deux permet de répondre à la question que l'école se pose quand une famille se
 * plaint d'être harcelée : est-ce le rappel automatique, ou une campagne qu'on a lancée ?
 */
public enum ReminderOrigin {

    /** Rappel programmé, à J-7 et J-1 de l'échéance. */
    AUTOMATIC,

    /** Campagne lancée depuis le portail. */
    CAMPAIGN
}
