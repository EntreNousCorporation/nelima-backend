package com.ypyit.neoelima.domain.establishment.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;


import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Vue du parc pour la console YPYit.
 *
 * <p>Complète {@code /dashboard/summary}, qui rend déjà les mêmes agrégats sans portée
 * d'établissement lorsqu'un administrateur l'appelle. Ce qui manquait tient en deux choses : la
 * commission — le seul revenu de Nelima, absent du tableau de bord d'une école qui ne la perçoit
 * pas — et la ventilation par établissement, sans laquelle un total ne dit pas quelle école le
 * porte.
 */
@Getter
@Setter
@Builder
public class PlatformOverviewDto {

    private long schoolCount;
    private long activeSchoolCount;

    /** Commission encaissée par YPYit, sur les paiements en ligne soldés du mois. */
    private BigDecimal commissionThisMonth;
    private BigDecimal commissionPreviousMonth;

    /** Part des encaissements passée par l'agrégateur : le reste est réglé au guichet. */
    private BigDecimal onlineCollectedThisMonth;

    /**
     * État du parc à la veille du mois, pour situer la croissance.
     *
     * <p>Compté sur la date de création, et non stocké : un instantané mensuel serait une table de
     * plus à tenir à jour, et faux dès qu'un enregistrement est supprimé.
     */
    private long schoolCountBeforeThisMonth;
    private long studentCountBeforeThisMonth;

    private List<SchoolRowDto> schools;

    @Getter
    @Setter
    @Builder
    public static class SchoolRowDto {
        private String id;
        private String name;
        private boolean active;

        /** Ville de l'école. Texte libre : elle situe, elle ne regroupe pas. */
        private String city;
        private long studentCount;
        private BigDecimal collectedThisMonth;

        /** Même mesure sur le mois précédent : c'est le seul terme de comparaison honnête. */
        private BigDecimal collectedPreviousMonth;

        /**
         * Attendu du mois selon les échéanciers.
         *
         * <p>Sans lui, le recouvrement d'une école ne se calcule pas : rapporter l'encaissé au seul
         * retard donnerait 100 % à une école qui n'a rien à recouvrer et rien encaissé.
         */
        private BigDecimal expectedThisMonth;
        private BigDecimal commissionThisMonth;
        private BigDecimal overdueAmount;
        private long overdueCount;

        /**
         * Contrat d'abonnement, nul tant qu'aucune formule n'est arrêtée.
         *
         * <p>Il ne voyage que par cette route, fermée aux écoles : le tarif consenti à l'une ne
         * regarde pas les autres, et une école n'a pas à lire ce que YPYit tire du parc.
         */
        private String subscriptionPlan;
        private LocalDate subscribedAt;

        /** Tarif courant de la formule. Une facture émise, elle, garde le sien. */
        private BigDecimal subscriptionAmount;
    }
}
