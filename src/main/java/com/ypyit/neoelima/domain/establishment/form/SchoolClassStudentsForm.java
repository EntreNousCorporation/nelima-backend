package com.ypyit.neoelima.domain.establishment.form;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class SchoolClassStudentsForm {

    /** Élèves à affecter à la classe. Ceux qui y sont déjà sont ignorés sans erreur. */
    @NotEmpty
    private List<UUID> studentIds;
}
