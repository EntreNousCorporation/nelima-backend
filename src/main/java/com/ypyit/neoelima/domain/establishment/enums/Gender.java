package com.ypyit.neoelima.domain.establishment.enums;

/**
 * Le sexe de l'élève.
 *
 * <p>Deux valeurs, parce que c'est ce que les états scolaires ivoiriens demandent — la répartition
 * filles/garçons figure sur les remontées au ministère et sur les statistiques d'établissement.
 * L'information reste <strong>facultative</strong> : elle manque sur les élèves déjà inscrits, et
 * la deviner serait pire que l'absence.
 */
public enum Gender {

    MALE,

    FEMALE
}
