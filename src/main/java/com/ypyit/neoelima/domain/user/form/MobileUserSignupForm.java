package com.ypyit.neoelima.domain.user.form;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ypyit.neoelima.common.validator.NoXssContent;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
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
public class MobileUserSignupForm {

    @NoXssContent
    @Schema(example = "Julien", requiredMode = Schema.RequiredMode.REQUIRED)
    private String firstName;
    @NoXssContent
    @Schema(example = "MIGNAUX", requiredMode = Schema.RequiredMode.REQUIRED)
    private String lastName;
    @NotEmpty
    private Set<@Valid ContactCreationForm> contacts;
    @JsonIgnore
    private boolean sendEmail;
}
