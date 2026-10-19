package com.ypyit.neoelima.domain.transport.repository;

import com.ypyit.neoelima.domain.transport.entity.TransportLineEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransportLineRepository extends JpaRepository<TransportLineEntity, UUID> {

    List<TransportLineEntity> findByEstablishment_IdOrderByNameAsc(UUID establishmentId);
}
