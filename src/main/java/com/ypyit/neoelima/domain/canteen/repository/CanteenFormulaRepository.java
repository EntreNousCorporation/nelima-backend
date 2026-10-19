package com.ypyit.neoelima.domain.canteen.repository;

import com.ypyit.neoelima.domain.canteen.entity.CanteenFormulaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CanteenFormulaRepository extends JpaRepository<CanteenFormulaEntity, UUID> {

    List<CanteenFormulaEntity> findByEstablishment_IdOrderByNameAsc(UUID establishmentId);
}
