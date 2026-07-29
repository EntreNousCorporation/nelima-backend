package com.ypyit.neoelima.domain.establishment.form;

import com.ypyit.neoelima.common.validator.NoXssContent;
import com.ypyit.neoelima.common.validator.NullCheck;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
public class EstablishmentLevelOfStudyCreationForm {

    @NoXssContent
    private String startLevelOfStudy;
    @NoXssContent
    private String endLevelOfStudy;
    @Valid
    @NullCheck
    private Set<@NotBlank String> levelOfStudiesCodes;
}
