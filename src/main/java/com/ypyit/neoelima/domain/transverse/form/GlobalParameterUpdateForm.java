package com.ypyit.neoelima.domain.transverse.form;

import com.ypyit.neoelima.common.validator.NoXssContent;
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
public class GlobalParameterUpdateForm {

    @NoXssContent
    private String name;
    @NoXssContent
    private String value;
}
