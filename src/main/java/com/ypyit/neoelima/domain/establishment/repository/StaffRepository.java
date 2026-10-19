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
     * L'annuaire. Les classes de l'année se lisent à part, en une requête pour tout l'établissement
     * ({@code StaffClassYearRepository}).
     */
    List<StaffEntity> findByEstablishment_IdOrderByLastNameAscFirstNameAsc(UUID establishmentId);

    List<StaffEntity> findByEstablishment_IdAndActiveTrueOrderByLastNameAscFirstNameAsc(UUID establishmentId);

    long countByEstablishment_IdAndActiveTrue(UUID establishmentId);
}
