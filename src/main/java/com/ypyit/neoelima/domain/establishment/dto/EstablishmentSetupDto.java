package com.ypyit.neoelima.domain.establishment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Où en est la configuration d'une école, vue par la console YPYit.
 *
 * <p>Chaque étape est déduite des données de l'école, jamais d'une case à cocher : une étape
 * « faite » sur une école sans niveau serait pire qu'aucune liste. La progression ne porte que sur
 * les étapes essentielles ; les modules complémentaires (cantine, transport…) disent ce que l'école
 * a déjà paramétré sans la pénaliser de ne pas s'en servir.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EstablishmentSetupDto {

    /** Étapes essentielles terminées, en pourcentage arrondi. */
    private int progress;

    private int done;

    private int total;

    /** Libellé de l'année scolaire en cours, sur laquelle portent classes, périodes et frais. */
    private String academicYear;

    private List<Step> steps;

    private List<Step> modules;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Step {

        private String key;

        private String label;

        /** Ce que l'étape recouvre, ou ce qu'il manque. */
        private String hint;

        /** Ce qui a été fait, chiffré : « 12 classes », « 210 élèves sur 350 »… */
        private String detail;

        private boolean done;

        private long count;
    }
}
