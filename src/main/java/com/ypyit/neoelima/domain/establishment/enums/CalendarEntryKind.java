package com.ypyit.neoelima.domain.establishment.enums;

/**
 * Nature d'une entrée du calendrier, telle que l'écran la filtre.
 *
 * <p>Distincte de {@link SchoolEventKind} : les deux premières valeurs viennent d'un événement
 * saisi, les deux dernières sont déduites — d'une tranche due pour l'une, de l'année scolaire
 * déclarée pour l'autre. Les confondre en une seule énumération laisserait croire qu'une échéance
 * ou un début de trimestre se crée à la main.
 */
public enum CalendarEntryKind {
    SCHOOL_LIFE,
    EXAM,
    FEE_DUE,
    ACADEMIC_PERIOD
}
