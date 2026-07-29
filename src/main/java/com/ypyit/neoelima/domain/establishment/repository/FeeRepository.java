package com.ypyit.neoelima.domain.establishment.repository;

import com.ypyit.neoelima.domain.establishment.entity.FeeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FeeRepository extends JpaRepository<FeeEntity, UUID>, QuerydslPredicateExecutor<FeeEntity> {

    boolean existsByEstablishment_IdAndNameIgnoreCase(UUID establishmentId, String name);

    Optional<FeeEntity> findByEstablishment_IdAndNameIgnoreCase(UUID establishmentId, String name);

    List<FeeEntity> findAllByEstablishment_Id(UUID establishmentId);
}
