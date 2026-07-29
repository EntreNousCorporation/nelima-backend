package com.ypyit.neoelima.domain.storage.form;

import com.ypyit.neoelima.common.validator.NoXssContent;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;


@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorageCreationForm {

    @NotBlank
    @NoXssContent
    private String fileName;
    @NotBlank
    @NoXssContent
    private String base64;
    @NotBlank
    @NoXssContent
    private String fileExtension;
    private UUID establishmentId;
}
