package com.ypyit.neoelima.domain.establishment.entity;

import com.ypyit.neoelima.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Ville où se situe un établissement.
 *
 * <p>Le libellé est la clé métier : c'est lui qui est écrit sur l'établissement et lu partout —
 * liste du parc, fiche, carte de contact servie aux familles. Un code n'aurait rien apporté qu'une
 * jointure à résoudre pour réafficher ce qui était déjà écrit, et une ville ne se renomme pas.
 *
 * <p>Le référentiel n'est pas administrable depuis la console : ouvrir la création d'une ville à
 * l'écran ramènerait la saisie libre par la porte de service, avec les orthographes multiples qu'on
 * vient d'éliminer. Une ville manquante s'ajoute par migration, comme une formule d'abonnement.
 */
@Entity
@Table(name = "city")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class CityEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @Column(nullable = false, length = 120)
    private String label;

    /**
     * Région administrative, pour regrouper la liste.
     *
     * <p>Nulle pour les saisies libres reprises à la mise en place du référentiel : leur inventer
     * une région aurait été une donnée fausse, et elles ne sont plus proposées.
     */
    @Column(length = 80)
    private String region;

    /** Une ville retirée du choix reste lisible sur les écoles qui la portent. */
    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
