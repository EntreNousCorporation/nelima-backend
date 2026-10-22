package com.ypyit.neoelima.domain.transport.repository;

import com.ypyit.neoelima.domain.transport.entity.TransportVehicleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TransportVehicleRepository extends JpaRepository<TransportVehicleEntity, UUID> {

    List<TransportVehicleEntity> findByEstablishment_IdOrderByPlateAsc(UUID establishmentId);

    List<TransportVehicleEntity> findByActiveTrue();
}
