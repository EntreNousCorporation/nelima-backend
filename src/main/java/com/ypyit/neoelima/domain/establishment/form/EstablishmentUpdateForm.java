package com.ypyit.neoelima.domain.establishment.form;

import com.ypyit.neoelima.common.validator.NoXssContent;
import com.ypyit.neoelima.domain.transverse.form.FileMediaUpdateForm;
import com.ypyit.neoelima.domain.user.form.ContactUpdateForm;
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
public class EstablishmentUpdateForm {

    @NoXssContent
    @Schema(example = "ypy-it", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String name;
    @NoXssContent
    @Schema(example = "https://ypy-it.com/", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String webSite;
    private boolean isPrimary;
    private Set<@Valid ContactUpdateForm> contacts;
    @Valid
    private FileMediaUpdateForm coverImage;
}
