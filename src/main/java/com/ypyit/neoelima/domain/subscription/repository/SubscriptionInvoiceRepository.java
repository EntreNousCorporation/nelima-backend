package com.ypyit.neoelima.domain.subscription.repository;

import com.ypyit.neoelima.domain.subscription.entity.SubscriptionInvoiceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionInvoiceRepository extends JpaRepository<SubscriptionInvoiceEntity, UUID> {

    List<SubscriptionInvoiceEntity> findByOrderByIssuedAtDesc();

    /** Factures déjà émises sous une formule : elles en interdisent la suppression. */
    long countByPlan(String plan);

    List<SubscriptionInvoiceEntity> findByEstablishment_IdOrderByPeriodStartDesc(UUID establishmentId);

    /**
     * Facture vivante d'une période, s'il y en a une.
     *
     * <p>Les annulées sont écartées : elles gardent leur numéro pour que la suite reste continue,
     * mais elles ne facturent plus rien. Une période dont l'unique facture a été annulée est donc
     * de nouveau à facturer — sans quoi annuler une facture émise par erreur la condamnerait.
     *
     * <p>Garde-fou doublé en base par l'index partiel {@code uk_subscription_invoice_period}.
     */
    Optional<SubscriptionInvoiceEntity> findByEstablishment_IdAndPeriodStartAndCancelledFalse(
            UUID establishmentId, LocalDate periodStart);
}
