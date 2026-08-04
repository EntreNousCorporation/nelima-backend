package com.ypyit.neoelima.domain.establishment.form;

import com.ypyit.neoelima.common.validator.ContactValueCheck;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@Builder
@ContactValueCheck
@NoArgsConstructor
@AllArgsConstructor
public class StudentSearchForm {

    private Set<String> levelOfStudies;

    /**
     * Texte libre : matricule, nom ou prénom, indifféremment.
     *
     * <p>Un champ par colonne obligerait le secrétariat à savoir dans lequel taper ; il tape ce
     * qu'il a sous les yeux, souvent le matricule d'un carnet.
     */
    private String keyword;

    /** Classe d'affectation. `unassigned` isole les élèves qui n'en ont pas encore. */
    private UUID schoolClassId;

    private Boolean unassignedOnly;
    private UUID establishmentId;
}
