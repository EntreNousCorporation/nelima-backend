package com.ypyit.neoelima.domain.establishment.form;

import com.ypyit.neoelima.common.validator.NoXssContent;
import com.ypyit.neoelima.domain.transverse.form.FileMediaCreateForm;
import com.ypyit.neoelima.domain.user.form.AddressCreationForm;
import com.ypyit.neoelima.domain.user.form.ContactCreationForm;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
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
public class EstablishmentRootCreationForm {

    @NotBlank
    @NoXssContent
    @Schema(example = "ECOBANK-CI", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;
    @NoXssContent
    @Schema(example = "https://ypy-it.com/", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String webSite;
    @Valid
    private AddressCreationForm address;

    /**
     * Ville de l'établissement.
     *
     * <p>Absente de ce formulaire jusqu'ici, alors que la console la demande dès la création et
     * l'envoyait : elle était donc reçue puis jetée en silence, et toute école créée depuis la
     * console naissait sans ville. Seule la fiche, qui passe par
     * {@link EstablishmentUpdateForm}, savait l'écrire.
     *
     * <p>Doit être un libellé du référentiel des villes — le service le vérifie.
     */
    @Size(max = 120)
    private String city;

    @Builder.Default
    private Boolean isPrimary = false;
    @NotEmpty
    private Set<@Valid ContactCreationForm> contacts;
    @Valid
    private FileMediaCreateForm coverImage;
}
