package com.ypyit.neoelima.domain.establishment.form;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SchoolClassForm {

    @NotBlank
    @Size(max = 64)
    private String name;

    @Size(max = 64)
    private String room;

    /**
     * Un effectif nul n'est pas une classe, et une salle de plus de trois cents élèves est une
     * faute de frappe : la borne haute évite qu'un zéro de trop passe pour une capacité.
     */
    @NotNull
    @Min(1)
    @Max(300)
    private Integer capacity;

    @Size(max = 128)
    private String mainTeacherName;

    /** Niveau enseigné dans la classe, parmi ceux que l'établissement a déclarés. */
    private String levelOfStudyCode;
}
