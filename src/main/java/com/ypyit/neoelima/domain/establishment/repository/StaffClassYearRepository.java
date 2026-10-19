package com.ypyit.neoelima.domain.establishment.repository;

import com.ypyit.neoelima.domain.establishment.entity.StaffClassYearEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StaffClassYearRepository extends JpaRepository<StaffClassYearEntity, UUID> {

    List<StaffClassYearEntity> findByStaff_IdAndAcademicYear_Id(UUID staffId, UUID academicYearId);

    List<StaffClassYearEntity> findByStaff_Id(UUID staffId);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "schoolClass")
    List<StaffClassYearEntity> findByStaff_Establishment_IdAndAcademicYear_Id(UUID establishmentId, UUID academicYearId);

    List<StaffClassYearEntity> findBySchoolClass_IdAndAcademicYear_Id(UUID schoolClassId, UUID academicYearId);

    boolean existsByStaff_IdAndSchoolClass_IdAndAcademicYear_Id(UUID staffId, UUID schoolClassId, UUID academicYearId);
}
