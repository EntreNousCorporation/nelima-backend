package com.ypyit.neoelima.domain.establishment.repository;

import com.ypyit.neoelima.domain.establishment.entity.AcademicYearEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AcademicYearRepository extends JpaRepository<AcademicYearEntity, UUID>,
        QuerydslPredicateExecutor<AcademicYearEntity> {

    @EntityGraph(attributePaths = "periods")
    List<AcademicYearEntity> findByEstablishment_IdOrderByStartDateDesc(UUID establishmentId);

    @EntityGraph(attributePaths = "periods")
    Optional<AcademicYearEntity> findByEstablishment_IdAndActiveTrue(UUID establishmentId);

    List<AcademicYearEntity> findByEstablishment_IdAndActiveTrueAndIdNot(UUID establishmentId, UUID id);
}
