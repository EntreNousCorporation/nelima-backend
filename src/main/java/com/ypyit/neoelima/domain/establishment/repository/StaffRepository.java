package com.ypyit.neoelima.domain.establishment.repository;

import com.ypyit.neoelima.domain.establishment.entity.StaffEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;

import java.util.List;
import java.util.UUID;

public interface StaffRepository extends JpaRepository<StaffEntity, UUID>,
        QuerydslPredicateExecutor<StaffEntity> {

    /**
     * Les classes sont chargées avec l'annuaire.
     *
     * <p>Sans ce graphe, afficher les affectations coûterait une requête par membre : soixante
     * allers-retours pour une école de taille ordinaire, sur l'écran le plus consulté du module.
     */
    @EntityGraph(attributePaths = "classes")
    List<StaffEntity> findByEstablishment_IdOrderByLastNameAscFirstNameAsc(UUID establishmentId);

    @EntityGraph(attributePaths = "classes")
    List<StaffEntity> findByEstablishment_IdAndActiveTrueOrderByLastNameAscFirstNameAsc(UUID establishmentId);

    long countByEstablishment_IdAndActiveTrue(UUID establishmentId);
}
