package com.ypyit.neoelima.domain.establishment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Une ville proposée au choix.
 *
 * <p>Sans identifiant : c'est le libellé qui est enregistré sur l'établissement, et le renvoyer
 * laisserait croire à l'appelant qu'il doit poster l'un ou l'autre.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CityDto {

    private String label;

    /** Région administrative, pour grouper la liste. Nulle pour les villes reprises. */
    private String region;
}
