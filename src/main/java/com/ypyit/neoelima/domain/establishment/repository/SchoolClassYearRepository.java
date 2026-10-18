package com.ypyit.neoelima.domain.establishment.repository;

import com.ypyit.neoelima.domain.establishment.entity.SchoolClassYearEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SchoolClassYearRepository extends JpaRepository<SchoolClassYearEntity, UUID> {

    boolean existsBySchoolClass_IdAndAcademicYear_Id(UUID schoolClassId, UUID academicYearId);
}
