package com.ypyit.neoelima.domain.establishment.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Une tranche telle que l'école la saisit. L'ordre de la liste fixe le rang, l'école n'a pas à
 * gérer de numérotation.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeeScheduleForm {

    @Schema(example = "1er versement")
    private String label;

    @NotNull
    @Positive
    @Schema(example = "50000", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal amount;

    @NotNull
    @Schema(example = "2026-10-15", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDate dueDate;
}
