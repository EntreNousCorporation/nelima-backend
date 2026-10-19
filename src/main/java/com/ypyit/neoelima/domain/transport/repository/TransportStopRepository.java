package com.ypyit.neoelima.domain.transport.repository;

import com.ypyit.neoelima.domain.transport.entity.TransportStopEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransportStopRepository extends JpaRepository<TransportStopEntity, UUID> {

    List<TransportStopEntity> findByLine_IdOrderByPositionAsc(UUID lineId);
}
