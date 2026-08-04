package com.ypyit.neoelima.domain.establishment.repository;

import com.ypyit.neoelima.domain.establishment.entity.ActivityEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;

import java.util.List;
import java.util.UUID;

public interface ActivityRepository extends JpaRepository<ActivityEntity, UUID>,
        QuerydslPredicateExecutor<ActivityEntity> {

    /**
     * Catalogue d'un établissement, classes visées comprises.
     *
     * <p>Sans ce graphe, afficher la colonne « Classes » coûterait une requête par activité, sur
     * l'écran principal du module.
     */
    @EntityGraph(attributePaths = {"eligibleClasses", "coach", "fee"})
    List<ActivityEntity> findByEstablishment_IdOrderByNameAsc(UUID establishmentId);

    boolean existsByEstablishment_IdAndNameIgnoreCase(UUID establishmentId, String name);
}
