package com.ypyit.neoelima.domain.establishment.form;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

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

    /**
     * Titulaire, désigné parmi le personnel de l'établissement.
     *
     * <p>Nul retire le titulaire : une classe entre deux enseignants n'en a pas.
     */
    private UUID mainTeacherId;

    /**
     * Nom du titulaire, pour les classes créées avant le répertoire du personnel.
     *
     * <p>N'est pris en compte que si aucun titulaire n'est désigné. Conservé le temps que les
     * écoles renseignent leur personnel ; la référence est la forme vers laquelle on va.
     */
    @Size(max = 128)
    private String mainTeacherName;

    /** Niveau enseigné dans la classe, parmi ceux que l'établissement a déclarés. */
    private String levelOfStudyCode;
}
