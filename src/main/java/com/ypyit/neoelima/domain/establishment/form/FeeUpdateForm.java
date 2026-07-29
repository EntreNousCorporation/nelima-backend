package com.ypyit.neoelima.domain.establishment.form;

import com.ypyit.neoelima.common.validator.NoXssContent;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

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
}
