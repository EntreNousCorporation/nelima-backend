package com.ypyit.neoelima.domain.user.form;

import com.ypyit.neoelima.common.validator.NoXssContent;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InitResetPasswordForm {

    @NotBlank
    @NoXssContent
    @Schema(example = "domain@email.com / +2250707070707", description = "Must be a valid email address or phone number with prefix", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;
}
