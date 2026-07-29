package com.ypyit.neoelima.domain.establishment.repository;

import com.ypyit.neoelima.domain.establishment.entity.StudentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface StudentRepository extends JpaRepository<StudentEntity, UUID>, QuerydslPredicateExecutor<StudentEntity> {

    boolean existsByEstablishment_IdAndRegistrationNumber(UUID establishmentId, String registrationNumber);

    Optional<StudentEntity> findByEstablishment_IdAndRegistrationNumber(UUID establishmentId, String registrationNumber);

    List<StudentEntity> findByEstablishment_IdAndLevelOfStudy_CodeIn(UUID establishmentId, Set<String> codes);

    Page<StudentEntity> findAllByEstablishment_Id(UUID establishmentId, Pageable pageable);

    List<StudentEntity> findByParentUsers_Id(UUID parentId);
}
