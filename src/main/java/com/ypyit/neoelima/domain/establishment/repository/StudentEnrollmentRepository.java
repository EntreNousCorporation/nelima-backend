package com.ypyit.neoelima.domain.establishment.repository;

import com.ypyit.neoelima.domain.establishment.entity.StudentEnrollmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StudentEnrollmentRepository extends JpaRepository<StudentEnrollmentEntity, UUID> {

    Optional<StudentEnrollmentEntity> findByStudent_IdAndAcademicYear_Id(UUID studentId, UUID academicYearId);

    long countByAcademicYear_IdAndStudent_Establishment_Id(UUID academicYearId, UUID establishmentId);

    boolean existsByAcademicYear_Id(UUID academicYearId);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"schoolClass", "levelOfStudy"})
    java.util.List<StudentEnrollmentEntity> findByAcademicYear_IdAndStudent_IdIn(UUID academicYearId, java.util.Collection<UUID> studentIds);

    java.util.List<StudentEnrollmentEntity> findByAcademicYear_IdAndSchoolClass_Id(UUID academicYearId, UUID schoolClassId);

    long countByAcademicYear_IdAndSchoolClass_Id(UUID academicYearId, UUID schoolClassId);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"student", "schoolClass", "levelOfStudy"})
    java.util.List<StudentEnrollmentEntity> findByAcademicYear_IdAndStudent_Establishment_Id(UUID academicYearId, UUID establishmentId);
}
