package com.ypyit.neoelima.domain.establishment.repository;

import com.ypyit.neoelima.domain.establishment.entity.CityEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CityRepository extends JpaRepository<CityEntity, UUID> {

    List<CityEntity> findByActiveTrueOrderByLabelAsc();

    boolean existsByLabelAndActiveTrue(String label);

    boolean existsByLabel(String label);
}
