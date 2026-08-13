package com.ypyit.neoelima.domain.establishment.form;

import com.ypyit.neoelima.domain.establishment.enums.Gender;
import com.ypyit.neoelima.common.validator.NoXssContent;
import com.ypyit.neoelima.domain.user.form.MobileUserSignupForm;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentCreationForm {

    @NotBlank
    @NoXssContent
    private String firstName;
    @NotBlank
    @NoXssContent
    private String lastName;
    @NotBlank
    @NoXssContent
    private String placeOfBirth;

    /** Facultatif : une école peut compléter l'état civil plus tard. */
    private Gender gender;
    @NotBlank
    @NoXssContent
    private String registrationNumber;
    @NotNull
    private LocalDate birthDay;
    @NotNull
    private String levelOfStudyCode;
    /**
     * Facultatif pour un utilisateur d'établissement : le serveur impose le sien et ignore la
     * valeur reçue. Seul un administrateur YPYit, qui n'est rattaché à aucun établissement, doit
     * le renseigner pour désigner sa cible.
     */
    private UUID establishmentId;
    private UUID parentId;
    @Valid
    private MobileUserSignupForm parent;
}
