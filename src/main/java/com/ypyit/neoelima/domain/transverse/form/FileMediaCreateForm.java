package com.ypyit.neoelima.domain.transverse.form;

import com.ypyit.neoelima.common.validator.FileExtensionCheck;
import com.ypyit.neoelima.common.validator.NoXssContent;
import jakarta.validation.constraints.NotBlank;
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
public class FileMediaCreateForm {

    @NotBlank
    @NoXssContent
    private String name;
    @NotBlank
    @NoXssContent
    private String base64;
    @NotBlank
    @NoXssContent
    @FileExtensionCheck
    private String extension;
}
