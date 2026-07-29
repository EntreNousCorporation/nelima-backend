package com.ypyit.neoelima.domain.establishment.form;

import com.ypyit.neoelima.common.validator.NoXssContent;
import com.ypyit.neoelima.common.validator.NullCheck;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeeCreationForm {

    /** Facultatif pour un utilisateur d'établissement : le serveur impose le sien. */
    private UUID establishmentId;
    @NoXssContent
    private String startLevelOfStudy;
    @NoXssContent
    private String endLevelOfStudy;
    @NotBlank
    @NoXssContent
    private String name;
    @NotNull
    @Positive
    private BigDecimal price;
    private boolean optional;
    private boolean academical;
    @Valid
    @NullCheck
    private Set<@NotBlank String> levelOfStudiesCodes;
}
