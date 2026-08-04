package com.ypyit.neoelima.domain.establishment.enums;

/**
 * Cycle du système éducatif ivoirien.
 *
 * <p>C'est la maille à laquelle une école raisonne quand elle regarde ses effectifs : « combien au
 * primaire », et non « combien en CE2 ». Le niveau reste la maille d'inscription d'un élève ; le
 * cycle est celle des chiffres d'ensemble.
 */
public enum EducationCycle {
    KINDERGARTEN,
    PRIMARY,
    MIDDLE_SCHOOL,
    HIGH_SCHOOL
}
