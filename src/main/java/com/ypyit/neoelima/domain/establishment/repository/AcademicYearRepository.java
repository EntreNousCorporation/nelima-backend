package com.ypyit.neoelima.domain.establishment.repository;

import com.ypyit.neoelima.domain.establishment.entity.AcademicYearEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AcademicYearRepository extends JpaRepository<AcademicYearEntity, UUID>,
        QuerydslPredicateExecutor<AcademicYearEntity> {

    List<AcademicYearEntity> findAllByOrderByStartDateDesc();

    Optional<AcademicYearEntity> findByActiveTrue();

    List<AcademicYearEntity> findByActiveTrueAndIdNot(UUID id);

    boolean existsByLabelIgnoreCaseAndIdNot(String label, UUID id);

    boolean existsByLabelIgnoreCase(String label);
}
