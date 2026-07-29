package com.ypyit.neoelima.domain.payment.form;

import com.ypyit.neoelima.domain.payment.enums.PaymentChannel;
import io.swagger.v3.oas.annotations.media.Schema;
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
}
