package com.ypyit.neoelima.domain.user.form;

import com.ypyit.neoelima.common.validator.NoXssContent;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserUpdateForm {

    @NoXssContent
    @Schema(example = "Julien", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String firstName;
    @NoXssContent
    @Schema(example = "MIGNAUX", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String lastName;
    private Set<@Valid ContactUpdateForm> contacts;
}
