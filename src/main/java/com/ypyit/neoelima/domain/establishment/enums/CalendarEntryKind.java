package com.ypyit.neoelima.domain.establishment.enums;

/**
 * Nature d'une entrée du calendrier, telle que l'écran la filtre.
 *
 * <p>Distincte de {@link SchoolEventKind} : les deux premières valeurs viennent d'un événement
 * saisi, la troisième d'une tranche due. Les confondre en une seule énumération laisserait croire
 * qu'une échéance se crée à la main.
 */
public enum CalendarEntryKind {
    SCHOOL_LIFE,
    EXAM,
    FEE_DUE
}
