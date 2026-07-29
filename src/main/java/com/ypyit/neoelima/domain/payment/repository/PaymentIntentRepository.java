package com.ypyit.neoelima.domain.payment.repository;

import com.ypyit.neoelima.domain.payment.entity.PaymentIntentEntity;
import com.ypyit.neoelima.domain.payment.enums.PaymentIntentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentIntentRepository extends JpaRepository<PaymentIntentEntity, UUID>,
        QuerydslPredicateExecutor<PaymentIntentEntity> {

    /** Point d'entrée du traitement de webhook : PaySwitch ne connaît que cette référence. */
    Optional<PaymentIntentEntity> findByInternalReference(String internalReference);

    /**
     * Tentatives restées en attente au-delà du délai donné. PaySwitch n'expose pas de relecture
     * forcée du statut auprès de l'agrégateur, ces lignes doivent donc être réconciliées.
     */
    List<PaymentIntentEntity> findByStatusAndCreatedAtBefore(PaymentIntentStatus status, Instant threshold);
}
