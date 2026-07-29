package com.ypyit.neoelima.domain.transverse.repository;


import com.ypyit.neoelima.domain.transverse.entity.GlobalParameterEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;


@Repository
public interface GlobalParameterRepository extends JpaRepository<GlobalParameterEntity, UUID> {

    boolean existsByCode(String code);

    Optional<GlobalParameterEntity> findByCode(String code);

    Optional<GlobalParameterEntity> findByNameIgnoreCase(String name);
}
