package com.ypyit.neoelima.domain.establishment.enums;

/**
 * État d'une activité au catalogue.
 *
 * <p>Le brouillon existe pour qu'une école prépare sa rentrée sans que les familles voient une
 * offre qu'elle n'a pas encore arrêtée. La suspension, elle, ne supprime rien : les inscrits le
 * restent et leurs dettes aussi, seules les nouvelles inscriptions sont refusées.
 */
public enum ActivityStatus {
    DRAFT,
    ACTIVE,
    SUSPENDED
}
