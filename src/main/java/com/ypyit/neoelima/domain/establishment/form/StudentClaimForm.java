package com.ypyit.neoelima.domain.establishment.form;

import com.ypyit.neoelima.common.validator.NoXssContent;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Preuve fournie par un parent pour rattacher un enfant à son compte.
 *
 * <p>Le triplet établissement + matricule + date de naissance tient lieu de preuve : il n'est pas
 * devinable, contrairement à un identifiant technique, et l'école n'a rien à faire pour que le
 * parent s'inscrive. La recherche par nom est volontairement exclue — elle permettrait d'énumérer
 * les élèves d'un établissement.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentClaimForm {

    @NotNull
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private UUID establishmentId;

    @NotBlank
    @NoXssContent
    @Schema(example = "2022333", requiredMode = Schema.RequiredMode.REQUIRED)
    private String registrationNumber;

    @NotNull
    @Schema(example = "2012-04-03", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDate birthDay;
}
