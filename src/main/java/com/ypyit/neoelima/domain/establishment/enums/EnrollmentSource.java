package com.ypyit.neoelima.domain.establishment.enums;

/**
 * Qui a demandé l'inscription.
 *
 * <p>Porté dès la première version, alors que seul le portail école inscrit : ajouter cette
 * distinction plus tard obligerait à trancher, sur des données déjà écrites, l'origine
 * d'inscriptions que plus personne ne saurait attribuer.
 */
public enum EnrollmentSource {
    SCHOOL,
    PARENT
}
