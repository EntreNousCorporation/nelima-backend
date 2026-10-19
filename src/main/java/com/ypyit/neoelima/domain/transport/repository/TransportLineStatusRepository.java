package com.ypyit.neoelima.domain.transport.repository;

import com.ypyit.neoelima.domain.transport.entity.TransportLineStatusEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransportLineStatusRepository extends JpaRepository<TransportLineStatusEntity, UUID> {

    Optional<TransportLineStatusEntity> findByLine_IdAndDay(UUID lineId, LocalDate day);

    List<TransportLineStatusEntity> findByLine_Establishment_IdAndDay(UUID establishmentId, LocalDate day);
}
