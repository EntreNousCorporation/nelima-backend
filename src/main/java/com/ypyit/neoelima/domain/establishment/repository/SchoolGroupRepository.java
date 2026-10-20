package com.ypyit.neoelima.domain.establishment.repository;

import com.ypyit.neoelima.domain.establishment.entity.SchoolGroupEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SchoolGroupRepository extends JpaRepository<SchoolGroupEntity, UUID> {

    boolean existsByNameIgnoreCase(String name);
}
