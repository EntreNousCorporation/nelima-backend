package com.ypyit.neoelima.domain.establishment.form;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Classes autorisées à participer.
 *
 * <p>Remplace l'affectation entière plutôt que d'ajouter : l'écran présente des cases à cocher, et
 * une opération d'ajout obligerait à deviner ce qui a été décoché.
 */
@Getter
@Setter
public class ActivityClassesForm {

    /** Ouverte à tout l'établissement : la liste de classes est alors ignorée. */
    private boolean openToAll;

    private List<UUID> classIds = new ArrayList<>();
}
