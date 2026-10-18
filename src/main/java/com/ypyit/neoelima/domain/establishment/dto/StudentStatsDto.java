package com.ypyit.neoelima.domain.establishment.dto;

import com.ypyit.neoelima.domain.establishment.enums.EducationCycle;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

/**
 * L'effectif de l'école vu d'en haut : ce que montrent les cartes, les graphiques et les onglets
 * de l'écran Élèves.
 *
 * <p>Calculé sur tous les élèves, pas sur la page affichée : « Sans classe : 3 » ne doit pas
 * vouloir dire « 3 sur les vingt lignes que vous voyez ».
 */
@Getter
@Builder
public class StudentStatsDto {
    private long total;
    /** Inscrits depuis le premier du mois. */
    private long newThisMonth;
    private long incompleteDossier;
    private long withoutClass;
    private long overdueCount;
    private BigDecimal overdueAmount;
    private long classCount;

    /** Par cycle, dans l'ordre de la scolarité ; un niveau non renseigné compte à part (cycle nul). */
    private List<CycleCount> byCycle;

    private long female;
    private long male;
    private long genderUnknown;

    /**
     * Statut de chaque élève, exclusif : la somme fait l'effectif.
     *
     * <p>Le dossier incomplet l'emporte, puis le retard, puis l'acompte : un élève n'est compté
     * qu'une fois, dans ce qui demande d'abord à l'école d'agir.
     */
    private long statusIncomplete;
    private long statusOverdue;
    private long statusDeposit;
    private long statusUpToDate;

    @Getter
    @Builder
    public static class CycleCount {
        private EducationCycle cycle;
        private long count;
    }
}
