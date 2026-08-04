package com.ypyit.neoelima.domain.establishment.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
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

    private List<SchoolRowDto> schools;

    @Getter
    @Setter
    @Builder
    public static class SchoolRowDto {
        private String id;
        private String name;
        private boolean active;
        private long studentCount;
        private BigDecimal collectedThisMonth;
        private BigDecimal commissionThisMonth;
        private BigDecimal overdueAmount;
        private long overdueCount;
    }
}
