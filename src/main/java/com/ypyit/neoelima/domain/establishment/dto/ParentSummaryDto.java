package com.ypyit.neoelima.domain.establishment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Ce qu'un parent voit en ouvrant l'application.
 *
 * <p>Un seul aller-retour, et des totaux agrégés en base. L'application aurait pu additionner
 * elle-même une page de tranches, mais elle en reçoit cinquante au plus : le total resterait juste
 * tant qu'une famille n'a que deux enfants, puis deviendrait faux sans rien annoncer. Un montant
 * d'argent affiché à un parent ne se permet pas ce genre de silence.
 *
 * <p>Distinct de {@link DashboardSummaryDto}, qui répond à une autre question : l'école regarde son
 * recouvrement, le parent regarde ce qu'il doit. Les deux se ressemblent de loin et n'ont ni la
 * même portée — élève par élève contre établissement entier — ni les mêmes chiffres.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParentSummaryDto {

    /**
     * Somme des tranches encore dues dont l'échéance tombe dans le mois courant.
     *
     * <p>C'est le montant de la carte d'accueil. Il ne comprend donc pas ce qui est dû plus tard,
     * ni ce qui l'était déjà avant le premier du mois : le premier n'est pas encore à régler, le
     * second se lit dans {@code overdueAmount}, et les confondre ferait afficher au parent une
     * somme qu'il ne reconnaît pas.
     */
    private BigDecimal dueThisMonth;

    /** Nombre de tranches composant {@code dueThisMonth} — le « 2 paiements à régler » du bandeau. */
    private long dueThisMonthCount;

    /**
     * Prochaine échéance à venir, tous enfants confondus.
     *
     * <p>Nulle quand plus rien n'est dû : l'accueil n'affiche alors pas de bandeau d'alerte.
     */
    private LocalDate nextDueDate;

    /** Reste dû, toutes échéances confondues, y compris les mois suivants. */
    private BigDecimal outstandingAmount;

    /** Part du reste dû déjà échue. C'est elle qui rend le bandeau rouge plutôt qu'ambre. */
    private BigDecimal overdueAmount;

    private long childrenCount;

    @Schema(description = "Les enfants rattachés, du plus urgent au moins urgent")
    private List<ParentChildDto> children;

    /**
     * Une ligne « Mes enfants ».
     *
     * <p>Ni photo ni identifiant d'établissement : la liste sert à reconnaître son enfant et à voir
     * ce qu'il reste à payer pour lui. Tout le reste se trouve sur sa fiche.
     */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParentChildDto {
        private UUID id;
        private String firstName;
        private String lastName;
        private String registrationNumber;

        /**
         * Classe d'affectation — « CM1 A ».
         *
         * <p>Nulle tant que l'élève n'est pas réparti, ce qui arrive à toute inscription en cours
         * d'année. L'application retombe alors sur {@code levelLabel}.
         */
        private String className;

        /** Niveau, en français — « CM1 ». Toujours servi, lui. */
        private String levelLabel;

        private String establishmentName;

        /** Reste dû pour cet enfant, toutes échéances confondues. Zéro, jamais nul. */
        private BigDecimal outstandingAmount;

        /** Prochaine échéance de cet enfant : c'est elle qui donne le badge « J-1 ». */
        private LocalDate nextDueDate;
    }
}
