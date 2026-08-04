package com.ypyit.neoelima.domain.establishment.enums;

/**
 * Famille d'activité extra-scolaire.
 *
 * <p>Sert à filtrer un catalogue qui compte vite une vingtaine d'entrées. L'intitulé exact reste
 * libre — « Judo », « Chorale », « Soutien maths 3e » — parce qu'aucune liste fermée ne couvrirait
 * ce qu'une école invente d'une année sur l'autre.
 */
public enum ActivityKind {
    SPORT,
    ARTS,
    LANGUAGE,
    TUTORING
}
