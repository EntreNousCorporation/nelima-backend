package com.ypyit.neoelima.domain.establishment.repository;

import com.ypyit.neoelima.domain.establishment.entity.SchoolYearEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SchoolYearRepository extends JpaRepository<SchoolYearEntity, UUID> {

    @EntityGraph(attributePaths = "periods")
    Optional<SchoolYearEntity> findByEstablishment_IdAndAcademicYear_Id(UUID establishmentId, UUID academicYearId);

    @EntityGraph(attributePaths = "periods")
    List<SchoolYearEntity> findByEstablishment_Id(UUID establishmentId);

    boolean existsByAcademicYear_Id(UUID academicYearId);
}
