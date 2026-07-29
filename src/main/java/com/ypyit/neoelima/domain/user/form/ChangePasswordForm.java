package com.ypyit.neoelima.domain.user.form;

import com.ypyit.neoelima.common.validator.NoXssContent;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
public class ChangePasswordForm {

    @Email
    @NotBlank
    @NoXssContent
    @Schema(example = "domain@email.com", description = "Must be a valid email address", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;
    @NotBlank
    @NoXssContent
    @Size(min = 6, max = 16)
    @Schema(example = "Api@2023", description = "Must be 6 characters long and combination of uppercase letters, lowercase letters, numbers, special characters", requiredMode = Schema.RequiredMode.REQUIRED)
    private String oldPassword;
    @NotBlank
    @NoXssContent
    @Size(min = 6, max = 16)
    @Schema(example = "Api@2023", description = "Must be 6 characters long and combination of uppercase letters, lowercase letters, numbers, special characters", requiredMode = Schema.RequiredMode.REQUIRED)
    private String newPassword;
}
