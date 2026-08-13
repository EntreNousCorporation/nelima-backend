package com.ypyit.neoelima.domain.establishment.form;

import com.ypyit.neoelima.common.validator.NoXssContent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Set;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeeUpdateForm {

    @NoXssContent
    private String name;
    @Positive
    private BigDecimal price;
    private boolean optional;
    private boolean academical;

    /**
     * Les niveaux auxquels le frais s'applique, tels qu'ils doivent être <strong>après</strong>
     * la modification.
     *
     * <p>Nul — et non vide — pour ne pas y toucher : un ensemble vide serait indiscernable de
     * « retirer tous les niveaux », et une école qui corrige un montant ne s'attend pas à voir le
     * frais se détacher de toutes ses classes.
     *
     * <p>Ce champ manquait, si bien qu'un frais créé sur le mauvais niveau ne se rattrapait qu'en
     * le supprimant.
     */
    private Set<@NotBlank String> levelOfStudiesCodes;
}
