package com.ypyit.neoelima.domain.establishment.form;

import com.ypyit.neoelima.common.validator.NoXssContent;
import com.ypyit.neoelima.domain.transverse.form.FileMediaUpdateForm;
import com.ypyit.neoelima.domain.user.form.ContactUpdateForm;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
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
public class EstablishmentUpdateForm {

    @NoXssContent
    @Schema(example = "ypy-it", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String name;
    @NoXssContent
    @Schema(example = "https://ypy-it.com/", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Size(max = 32)
    private String shortName;

    @Size(max = 64)
    private String accreditationNumber;

    /**
     * Adresse du siège.
     *
     * <p>Absente du formulaire jusqu'ici, alors que l'entité la porte et qu'elle figure sur les
     * reçus : une école ne pouvait pas corriger la sienne.
     */
    private String addressName;

    /** Ville de l'établissement. Nulle tant qu'elle n'a pas été renseignée. */
    @Size(max = 120)
    private String city;

    private String webSite;

    /**
     * Établissement principal du groupe.
     *
     * <p>Objet et non primitive, délibérément : le mapper ignore les valeurs nulles, mais une
     * primitive n'est jamais nulle. Un appel qui omettait ce champ — l'écran des paramètres, qui
     * n'a rien à en dire — le recevait donc à {@code false} et rétrogradait l'établissement sans
     * que personne ne l'ait demandé.
     */
    private Boolean isPrimary;
    private Set<@Valid ContactUpdateForm> contacts;
    @Valid
    private FileMediaUpdateForm coverImage;
}
