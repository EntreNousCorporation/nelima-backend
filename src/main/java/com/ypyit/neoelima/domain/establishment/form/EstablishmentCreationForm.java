package com.ypyit.neoelima.domain.establishment.form;

import com.ypyit.neoelima.common.validator.NoXssContent;
import com.ypyit.neoelima.domain.transverse.form.FileMediaCreateForm;
import com.ypyit.neoelima.domain.user.form.AddressCreationForm;
import com.ypyit.neoelima.domain.user.form.ContactCreationForm;
import com.ypyit.neoelima.domain.user.form.EstablishmentPrincipalCreationForm;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EstablishmentCreationForm {

    @NotBlank
    @NoXssContent
    @Schema(example = "ECOBANK-CI", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;
    @NoXssContent
    @Schema(example = "https://ypy-it.com/", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String webSite;
    @Valid
    private AddressCreationForm address;
    @Builder.Default
    private Boolean isPrimary = false;
    @NotEmpty
    private Set<@Valid ContactCreationForm> contacts;
    @Valid
    private FileMediaCreateForm coverImage;
    @NotNull
    private UUID parentId;
    @Valid
    private EstablishmentPrincipalCreationForm principal;
}
