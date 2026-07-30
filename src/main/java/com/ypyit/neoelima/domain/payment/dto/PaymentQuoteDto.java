package com.ypyit.neoelima.domain.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Ce que coûtera le règlement d'une tranche, avant tout engagement.
 *
 * <p>Sert à afficher le détail au parent au moment où il choisit de payer. Les montants sont
 * calculés par le même code que l'initiation : c'est ce qui garantit que la somme annoncée à
 * l'écran est exactement celle qui sera débitée. Une commission recalculée côté application
 * finirait tôt ou tard par diverger de celle appliquée par le serveur.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentQuoteDto {

    /** Part reversée à l'école : le montant de la tranche. */
    private BigDecimal amountSchool;

    /** Part YPYit, ajoutée au montant de la tranche. */
    private BigDecimal amountCommission;

    /** Somme réellement débitée au payeur. */
    private BigDecimal totalAmount;

    @Schema(example = "XOF")
    private String currency;
}
