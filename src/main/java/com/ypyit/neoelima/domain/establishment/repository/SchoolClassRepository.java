package com.ypyit.neoelima.domain.establishment.repository;

import com.ypyit.neoelima.domain.establishment.entity.SchoolClassEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;

import java.util.List;
import java.util.UUID;

public interface SchoolClassRepository extends JpaRepository<SchoolClassEntity, UUID>,
        QuerydslPredicateExecutor<SchoolClassEntity> {

    List<SchoolClassEntity> findByEstablishment_IdOrderByNameAsc(UUID establishmentId);
}
