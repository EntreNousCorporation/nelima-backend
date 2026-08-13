package com.ypyit.neoelima.domain.establishment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * Ce qu'une école voit en ouvrant son espace.
 *
 * <p>Les totaux sont calculés par la base et non par l'application : additionner une page de reçus
 * côté client donnerait un « encaissé du mois » faux dès que le mois dépasse la taille de la page,
 * et faux sans le dire.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardSummaryDto {

    /** Effectif inscrit dans l'établissement. */
    private long studentCount;

    /**
     * Nombre de niveaux déclarés par l'établissement.
     *
     * <p>Sert à la mise en route du tableau de bord, dont l'étape « Déclarer les niveaux
     * enseignés » se cochait jusqu'ici sur l'effectif — le même critère que l'étape suivante. Une
     * école pouvait donc déclarer ses niveaux sans que la liste en prenne acte, et l'étape ne
     * s'achevait qu'au premier élève inscrit.
     */
    private long levelCount;

    /**
     * Répartition filles/garçons, et ce qui reste à renseigner.
     *
     * <p>Les trois se donnent ensemble : sans {@code genderUnknownCount}, une école lirait « 12
     * filles, 9 garçons » sur un effectif de trente et croirait à un total, alors que neuf fiches
     * n'ont simplement pas l'information.
     */
    private long girlCount;

    private long boyCount;

    private long genderUnknownCount;

    /** Somme encaissée depuis le premier jour du mois courant, tous canaux confondus. */
    private BigDecimal collectedThisMonth;

    /** Nombre de reçus émis sur la même période. */
    private long receiptsThisMonth;

    /** Reste dû, toutes échéances confondues. */
    private BigDecimal pendingAmount;

    private long pendingCount;

    /** Part du reste dû dont l'échéance est déjà passée : c'est là que l'école doit agir. */
    private BigDecimal overdueAmount;

    private long overdueCount;

    /** Encaissements de la journée en cours, guichet compris. */
    private BigDecimal collectedToday;

    private long paymentsToday;

    /**
     * Somme attendue sur le mois : tranches dont l'échéance y tombe, réglées ou non.
     *
     * <p>C'est le dénominateur du taux de recouvrement. Le calculer sur les seules tranches encore
     * dues donnerait un taux qui monte quand une école encaisse — l'inverse de ce qu'il mesure.
     */
    private BigDecimal expectedThisMonth;

    /** Encaissé du mois précédent, pour situer le mois courant sans avoir à le chercher ailleurs. */
    private BigDecimal collectedPreviousMonth;

    /**
     * Attendu du mois précédent.
     *
     * <p>Sans lui, le recouvrement ne se compare pas : rapprocher deux encaissements bruts d'un
     * mois sur l'autre confond « on a mieux recouvré » et « il y avait plus à recouvrer ».
     */
    private BigDecimal expectedPreviousMonth;

    @Schema(description = "Six derniers mois, du plus ancien au plus récent")
    private List<MonthlyPointDto> monthly;

    /** Remplissage des classes, tel que l'école le lit d'un coup d'œil à la rentrée. */
    private List<ClassFillingDto> classFilling;

    @Schema(description = "Élèves aux retards les plus élevés, du plus gros solde au plus petit")
    private List<OverdueStudentDto> topOverdue;

    @Schema(description = "Derniers encaissements, du plus récent au plus ancien")
    private List<ReceiptSummaryDto> recentReceipts;

    /** Effectif d'une classe rapporté à sa capacité. */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClassFillingDto {
        private String id;
        private String name;
        private String levelLabel;
        private long studentCount;
        private Integer capacity;
    }

    /** Un mois de la série attendu / encaissé. */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthlyPointDto {
        /** Mois au format `AAAA-MM`, pour que le client n'ait pas à déduire une année. */
        private String month;
        private BigDecimal expected;
        private BigDecimal collected;
    }

    /** Un élève en retard de paiement, tel que l'école a besoin de le relancer. */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OverdueStudentDto {
        private String studentId;
        private String label;
        private String registrationNumber;

        /**
         * Rappels déjà envoyés à la famille, tous canaux et toutes tranches confondus.
         *
         * <p>C'est ce qui distingue une famille qu'on a oubliée d'une famille qui ne répond pas :
         * la première se relance, la seconde s'appelle.
         */
        @Builder.Default
        private long reminderCount = 0;
        private String levelCode;
        /** Retard de la plus ancienne échéance dépassée, en jours. */
        private long daysLate;
        private BigDecimal amount;
    }

    /**
     * Vue réduite d'un reçu pour la liste d'accueil.
     *
     * <p>Volontairement distincte du {@code ReceiptDto} complet : servir l'entité entière tirerait
     * la tentative de paiement, la tranche, l'élève et son établissement pour afficher trois
     * colonnes.
     */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReceiptSummaryDto {
        private String id;
        private String number;
        private BigDecimal amount;
        private String studentLabel;
        private java.time.Instant issuedAt;
    }
}
