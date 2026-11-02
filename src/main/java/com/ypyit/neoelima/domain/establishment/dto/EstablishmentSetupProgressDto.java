package com.ypyit.neoelima.domain.establishment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** Progression de la configuration d'une école, pour la liste du parc de la console YPYit. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EstablishmentSetupProgressDto {

    private UUID establishmentId;

    /** Étapes essentielles terminées, en pourcentage arrondi : le même que sur la fiche. */
    private int progress;

    private int done;

    private int total;
}
