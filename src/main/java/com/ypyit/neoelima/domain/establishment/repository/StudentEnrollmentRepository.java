package com.ypyit.neoelima.domain.establishment.repository;

import com.ypyit.neoelima.domain.establishment.entity.StudentEnrollmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StudentEnrollmentRepository extends JpaRepository<StudentEnrollmentEntity, UUID> {

    Optional<StudentEnrollmentEntity> findByStudent_IdAndAcademicYear_Id(UUID studentId, UUID academicYearId);

    long countByAcademicYear_IdAndStudent_Establishment_Id(UUID academicYearId, UUID establishmentId);

    boolean existsByAcademicYear_Id(UUID academicYearId);
}
