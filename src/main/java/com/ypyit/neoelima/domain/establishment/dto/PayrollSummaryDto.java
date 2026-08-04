package com.ypyit.neoelima.domain.establishment.dto;

import com.ypyit.neoelima.domain.establishment.enums.ContractType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Ce que coûte le personnel, et comment il est contractualisé.
 *
 * <p>Seul endroit où le total des rémunérations circule. Il n'est servi qu'aux appelants portant
 * {@code staff:read_salary}.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayrollSummaryDto {

    /** Somme des bruts mensuels renseignés. */
    private BigDecimal monthlyPayroll;

    /**
     * Nombre de membres dont le salaire est renseigné.
     *
     * <p>Sans lui, une masse salariale se lirait comme couvrant tout l'effectif alors qu'elle ne
     * couvre que les fiches complétées.
     */
    private long paidHeadcount;

    private long headcount;

    /** Moyenne sur les seuls salaires renseignés : diviser par l'effectif entier la tirerait vers zéro. */
    private BigDecimal averageSalary;

    @Builder.Default
    private Map<ContractType, Long> byContract = new HashMap<>();

    /** Membres sans type de contrat renseigné. */
    private long withoutContract;

    private BigDecimal averageSeniorityYears;

    private long weeklyTeachingHours;
}
