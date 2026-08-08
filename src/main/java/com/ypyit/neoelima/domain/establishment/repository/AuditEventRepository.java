package com.ypyit.neoelima.domain.establishment.repository;

import com.ypyit.neoelima.domain.establishment.entity.AuditEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AuditEventRepository extends JpaRepository<AuditEventEntity, UUID> {

    List<AuditEventEntity> findByEstablishment_IdAndOccurredAtBetweenOrderByOccurredAtDesc(
            UUID establishmentId, Instant from, Instant to);

    List<AuditEventEntity> findByEstablishment_IdAndActor_IdAndOccurredAtBetweenOrderByOccurredAtDesc(
            UUID establishmentId, UUID actorId, Instant from, Instant to);
}
