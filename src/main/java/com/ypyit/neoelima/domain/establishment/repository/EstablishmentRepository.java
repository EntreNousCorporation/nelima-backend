package com.ypyit.neoelima.domain.establishment.repository;

import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EstablishmentRepository extends JpaRepository<EstablishmentEntity, UUID>, QuerydslPredicateExecutor<EstablishmentEntity> {

    boolean existsByNameIgnoreCase(String name);

    Optional<EstablishmentEntity> findByNameIgnoreCase(String name);

    List<EstablishmentEntity> findByParent_Id(UUID parentId);
}
