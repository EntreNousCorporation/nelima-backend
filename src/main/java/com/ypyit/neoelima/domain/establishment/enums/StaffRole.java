package com.ypyit.neoelima.domain.establishment.enums;

/**
 * Famille de fonction, distincte de l'intitulé du poste.
 *
 * <p>L'intitulé est libre — « Instituteur CM2 », « Économat » — parce qu'aucune liste fermée ne
 * couvrirait les usages d'un établissement à l'autre. La famille, elle, sert à filtrer et à
 * compter : combien d'enseignants, combien d'administratifs.
 */
public enum StaffRole {
    TEACHER,
    ADMINISTRATION,
    DIRECTION,
    SUPPORT
}
