package com.ypyit.neoelima.domain.establishment.repository;

import com.ypyit.neoelima.domain.establishment.entity.SchoolClassYearEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SchoolClassYearRepository extends JpaRepository<SchoolClassYearEntity, UUID> {

    boolean existsBySchoolClass_IdAndAcademicYear_Id(UUID schoolClassId, UUID academicYearId);

    Optional<SchoolClassYearEntity> findBySchoolClass_IdAndAcademicYear_Id(UUID schoolClassId, UUID academicYearId);

    List<SchoolClassYearEntity> findByAcademicYear_IdAndSchoolClass_Establishment_Id(UUID academicYearId, UUID establishmentId);
}
