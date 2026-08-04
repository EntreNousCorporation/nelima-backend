package com.ypyit.neoelima.domain.establishment.enums;

/**
 * Pointage d'un membre du personnel sur une journée.
 *
 * <p>L'absence justifiée est distinguée de l'absence tout court : la première n'appelle pas de
 * remplacement en urgence, la seconde si.
 */
public enum AttendanceStatus {
    PRESENT,
    LATE,
    ABSENT,
    LEAVE
}
