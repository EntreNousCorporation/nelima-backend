package com.ypyit.neoelima.domain.establishment.enums;

/**
 * Nature d'un événement saisi par l'école.
 *
 * <p>L'échéance financière n'en fait pas partie : elle n'est jamais saisie, elle est déduite des
 * tranches dues. La stocker ici ferait une seconde vérité à côté de la dette réelle.
 */
public enum SchoolEventKind {

    /** Conseil de classe, réunion de parents, fermeture, sortie… */
    SCHOOL_LIFE,

    EXAM
}
