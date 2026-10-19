package com.ypyit.neoelima.domain.transport.repository;

import com.ypyit.neoelima.domain.transport.entity.TransportDriverEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransportDriverRepository extends JpaRepository<TransportDriverEntity, UUID> {

    List<TransportDriverEntity> findByEstablishment_IdOrderByLastNameAsc(UUID establishmentId);
}
