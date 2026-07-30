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

    @Schema(description = "Derniers encaissements, du plus récent au plus ancien")
    private List<ReceiptSummaryDto> recentReceipts;

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
