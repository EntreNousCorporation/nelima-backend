package com.ypyit.neoelima.domain.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Canal de paiement mobile proposé au parent.
 *
 * @param code  valeur transmise à l'agrégateur, telle qu'il l'attend
 * @param label libellé affiché dans l'application
 */
public record PaymentChannelDto(
        @Schema(example = "wave") String code,
        @Schema(example = "Wave") String label) {
}
