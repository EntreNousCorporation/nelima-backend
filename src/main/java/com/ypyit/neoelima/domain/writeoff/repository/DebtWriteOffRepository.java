package com.ypyit.neoelima.domain.writeoff.repository;

import com.ypyit.neoelima.domain.writeoff.entity.DebtWriteOffEntity;
import com.ypyit.neoelima.domain.writeoff.enums.WriteOffStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DebtWriteOffRepository extends JpaRepository<DebtWriteOffEntity, UUID> {

    List<DebtWriteOffEntity> findByEstablishment_IdOrderByProposedAtDesc(UUID establishmentId);

    List<DebtWriteOffEntity> findByEstablishment_IdAndStatus(UUID establishmentId, WriteOffStatus status);

    boolean existsByInstallment_IdAndStatus(UUID installmentId, WriteOffStatus status);
}
