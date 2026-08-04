package com.ypyit.neoelima.domain.establishment.enums;

/**
 * Canal par lequel un rappel est parti.
 *
 * <p>Le canal fait partie de la clé du registre : recevoir la même relance par notification et par
 * SMS n'est pas un doublon, c'est un choix de l'école. La recevoir deux fois par SMS en est un, et
 * il se paie.
 */
public enum ReminderChannel {
    PUSH,
    SMS
}
