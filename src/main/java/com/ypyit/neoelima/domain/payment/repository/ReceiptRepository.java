package com.ypyit.neoelima.domain.payment.repository;

import com.ypyit.neoelima.domain.payment.entity.ReceiptEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReceiptRepository extends JpaRepository<ReceiptEntity, UUID>,
        QuerydslPredicateExecutor<ReceiptEntity> {

    Optional<ReceiptEntity> findByPaymentIntent_Id(UUID paymentIntentId);

    /** Reçus de plusieurs tentatives, pour éviter une requête par ligne sur l'écran de rapprochement. */
    java.util.List<ReceiptEntity> findByPaymentIntent_IdIn(java.util.Collection<UUID> paymentIntentIds);

    boolean existsByEstablishment_IdAndSequenceNumber(UUID establishmentId, Long sequenceNumber);
}
