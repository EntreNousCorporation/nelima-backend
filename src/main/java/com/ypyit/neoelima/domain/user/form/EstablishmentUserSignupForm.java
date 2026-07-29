package com.ypyit.neoelima.domain.user.form;

import com.ypyit.neoelima.common.validator.NoXssContent;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class EstablishmentUserSignupForm {

    @NotBlank
    @NoXssContent
    @Schema(example = "Julien", requiredMode = Schema.RequiredMode.REQUIRED)
    private String firstName;
    @NotBlank
    @NoXssContent
    @Schema(example = "MIGNAUX", requiredMode = Schema.RequiredMode.REQUIRED)
    private String lastName;
    @NotNull
    @Schema(example = "6600b04f-6091-4390-b729-8d6704aa9ef7", requiredMode = Schema.RequiredMode.REQUIRED)
    private UUID roleId;
    @NotEmpty
    private Set<@Valid ContactCreationForm> contacts;
}
