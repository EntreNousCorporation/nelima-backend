package com.ypyit.neoelima.domain.payment.dto;

import com.ypyit.neoelima.common.dto.BaseDto;
import com.ypyit.neoelima.domain.payment.enums.PaymentChannel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptDto extends BaseDto {

    private String number;

    /** Montant total réglé, commission comprise. */
    private BigDecimal amount;

    private Instant issuedAt;
    private String studentLabel;
    private String studentRegistrationNumber;
    private String payerLabel;
    private PaymentChannel channel;

    /** Ce qui a été réglé — « Scolarité de Février ». */
    private String feeLabel;

    /** Classe de l'élève au jour du paiement. Nulle sur les reçus antérieurs à ce champ. */
    private String studentClassName;

    private String establishmentName;

    /**
     * Ventilation du total : la part de l'école et la commission de la plateforme.
     *
     * <p>Toutes deux figées à l'émission. Les recalculer au taux du jour donnerait, sur un
     * règlement ancien, une ventilation qui ne correspond à aucun mouvement réel.
     */
    private BigDecimal amountSchool;

    private BigDecimal amountCommission;

    /**
     * Opérateur choisi par la famille — {@code orange}, {@code wave}, {@code mtn}…
     *
     * <p>Nul au guichet, et nul sur les reçus émis avant que ce choix ne soit conservé. L'appelant
     * omet alors la ligne plutôt que d'afficher un tiret.
     */
    private String paymentMethod;
}
