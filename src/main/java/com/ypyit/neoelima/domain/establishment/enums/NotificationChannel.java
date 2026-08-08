package com.ypyit.neoelima.domain.establishment.enums;

/**
 * Canal par lequel une famille peut être jointe.
 *
 * <p>Trois canaux raccordés, et trois seulement. WhatsApp n'y figure pas : aucun raccordement
 * n'existe, et une case qui n'envoie rien se découvre à l'usage.
 */
public enum NotificationChannel {
    PUSH,
    EMAIL,
    SMS
}
