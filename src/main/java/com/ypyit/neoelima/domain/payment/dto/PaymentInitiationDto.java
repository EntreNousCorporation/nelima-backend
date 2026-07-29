package com.ypyit.neoelima.domain.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Ce que reçoit l'application après avoir demandé un paiement.
 *
 * <p>La décomposition part école / commission est renvoyée telle quelle : le parent doit voir ce
 * qu'il paie en plus du montant de la tranche avant de valider.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentInitiationDto {

    private UUID paymentIntentId;

    @Schema(example = "PSW-A1B2C3D4E5")
    private String internalReference;

    /** URL du tunnel de paiement de l'agrégateur, à ouvrir dans une webview. */
    private String checkoutUrl;

    private BigDecimal amountSchool;

    private BigDecimal amountCommission;

    private BigDecimal totalAmount;

    @Schema(example = "XOF")
    private String currency;
}
