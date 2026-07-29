package com.ypyit.neoelima.domain.user.form;

import com.ypyit.neoelima.common.validator.ContactValueCheck;
import com.ypyit.neoelima.common.validator.NoXssContent;
import com.ypyit.neoelima.domain.user.enums.ContactType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@ContactValueCheck
@NoArgsConstructor
@AllArgsConstructor
public class ContactCreationForm {

    @NotBlank
    @NoXssContent
    @Schema(example = "domain@email.com|+2250747752256", requiredMode = Schema.RequiredMode.REQUIRED)
    private String value;
    @NotNull
    private ContactType type;
    @Builder.Default
    private Boolean isPrimary = false;
    @Builder.Default
    private Boolean whatsApp = false;
}
