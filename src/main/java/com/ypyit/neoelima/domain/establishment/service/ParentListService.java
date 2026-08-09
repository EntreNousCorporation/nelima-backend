package com.ypyit.neoelima.domain.establishment.service;

import com.ypyit.neoelima.domain.establishment.dto.ParentListItemDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Listes de parents, selon la surface qui les demande.
 *
 * <p>Deux portées, jamais mélangées : le portail d'une école ne voit que les parents de ses propres
 * élèves, le back-office voit ceux de tout le parc. La portée est imposée par le service à partir de
 * l'appelant authentifié, jamais reçue en paramètre.
 */
public interface ParentListService {

    /**
     * Parents ayant au moins un enfant dans l'établissement de l'utilisateur connecté.
     *
     * <p>La portée est dérivée du compte appelant. Un utilisateur d'établissement est épinglé au
     * sien ; un administrateur YPYit qui appellerait cette route verrait tout le parc. Les enfants
     * listés sont ceux de cet établissement, et eux seuls : la famille peut avoir des enfants
     * ailleurs, l'école n'a pas à le savoir.
     *
     * @param keyword filtre optionnel sur nom, prénom ou contact du parent
     */
    Page<ParentListItemDto> searchForEstablishment(String keyword, Pageable pageable);

    /**
     * Tous les parents de la plateforme, réservé à l'équipe YPYit.
     *
     * @param keyword         filtre optionnel sur nom, prénom ou contact du parent
     * @param establishmentId ne retient que les parents ayant un enfant dans cet établissement ; les
     *                        enfants listés restent ceux de toute la famille, avec le nom de leur école
     */
    Page<ParentListItemDto> searchForPlatform(String keyword, UUID establishmentId, Pageable pageable);
}
