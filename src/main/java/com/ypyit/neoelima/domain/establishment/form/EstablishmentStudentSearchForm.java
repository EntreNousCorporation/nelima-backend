package com.ypyit.neoelima.domain.establishment.form;

import com.ypyit.neoelima.common.validator.ContactValueCheck;
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

@Getter
@Setter
@Builder
@ContactValueCheck
@NoArgsConstructor
@AllArgsConstructor
public class EstablishmentStudentSearchForm {

    @NotBlank
    @NoXssContent
    @Schema(example = "AZ1234", requiredMode = Schema.RequiredMode.REQUIRED)
    private String registrationNumber;
    @NotNull
    @Schema(example = "1998-10-12", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDate birthDay;
    @NotNull
    private UUID establishmentId;
}
