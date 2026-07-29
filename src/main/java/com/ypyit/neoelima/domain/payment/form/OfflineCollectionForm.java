package com.ypyit.neoelima.domain.payment.form;

import com.ypyit.neoelima.domain.payment.enums.PaymentChannel;
import io.swagger.v3.oas.annotations.media.Schema;
import com.ypyit.neoelima.common.validator.NoXssContent;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** Encaissement saisi au guichet par le comptable ou le secrétariat. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfflineCollectionForm {

    @NotNull
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private UUID installmentId;

    @NotNull
    @Schema(example = "CASH", requiredMode = Schema.RequiredMode.REQUIRED)
    private PaymentChannel channel;

    /** Numéro de chèque ou référence de virement, pour le rapprochement bancaire. */
    @Schema(example = "CHQ-4412")
    private String reference;

    /**
     * Nom de la personne qui règle, quand ce n'est pas l'agent au comptoir. Reporté sur le reçu :
     * une famille doit s'y reconnaître, pas y lire le nom du secrétariat.
     */
    @NoXssContent
    @Schema(example = "Jean Kouassi")
    private String payerName;

    /**
     * Email du payeur. Renseigné, le reçu lui est envoyé en PDF ; laissé vide, aucun email n'est
     * expédié — on n'envoie pas la pièce comptable d'une famille à l'adresse de l'établissement.
     */
    @Email
    @NoXssContent
    @Schema(example = "parent@example.com")
    private String payerEmail;
}
