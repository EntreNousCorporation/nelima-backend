package com.ypyit.neoelima.domain.user.form;

import com.ypyit.neoelima.common.validator.NoXssContent;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
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
public class AddressCreationForm {

    @NoXssContent
    @Schema(example = "VICTOR LOBAD", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;
    @NotNull
    @Schema(example = "-1222.111", requiredMode = Schema.RequiredMode.REQUIRED)
    private Double longitude;
    @NotNull
    @Schema(example = "19919.0", requiredMode = Schema.RequiredMode.REQUIRED)
    private Double latitude;
}
