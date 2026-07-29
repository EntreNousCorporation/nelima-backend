package com.ypyit.neoelima.domain.user.form;

import com.ypyit.neoelima.common.validator.NoXssContent;
import com.ypyit.neoelima.domain.establishment.form.EstablishmentRootCreationForm;
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

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class UserSignupForm {

    @NotBlank
    @NoXssContent
    @Schema(example = "Julien", requiredMode = Schema.RequiredMode.REQUIRED)
    private String firstName;
    @NotBlank
    @NoXssContent
    @Schema(example = "MIGNAUX", requiredMode = Schema.RequiredMode.REQUIRED)
    private String lastName;
    @NotEmpty
    private Set<@Valid ContactCreationForm> contacts;
    @Valid
    @NotNull
    private EstablishmentRootCreationForm establishment;
}
