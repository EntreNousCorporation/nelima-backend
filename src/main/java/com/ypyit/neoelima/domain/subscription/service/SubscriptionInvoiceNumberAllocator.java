package com.ypyit.neoelima.domain.subscription.service;

import com.ypyit.neoelima.domain.subscription.entity.SubscriptionInvoiceCounterEntity;
import com.ypyit.neoelima.domain.subscription.repository.SubscriptionInvoiceCounterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Attribue les numéros de facture d'abonnement, séquentiels par année civile.
 *
 * <p>Même exigence et même technique que pour les reçus des écoles : la suite doit être continue et
 * sans doublon. Le verrou {@code SELECT ... FOR UPDATE} fait attendre la seconde transaction au
 * lieu de lui laisser lire une valeur périmée ; un {@code max(sequence) + 1} serait sujet à une
 * course entre la lecture et l'écriture.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionInvoiceNumberAllocator {

    private final SubscriptionInvoiceCounterRepository counterRepository;

    /**
     * Réserve le prochain numéro de l'année, au format {@code NL-2026-0001}.
     *
     * <p>Doit être appelée dans la transaction qui persiste la facture : le verrou n'est relâché
     * qu'à la validation de celle-ci, ce qui empêche une autre transaction de réutiliser le numéro
     * si la première échoue et annule.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public String allocate(int year) {
        // L'ordre compte : un SELECT ... FOR UPDATE ne verrouille rien s'il ne trouve pas de ligne,
        // et deux premières émissions simultanées d'une année neuve créeraient chacune la leur.
        this.counterRepository.insertIfAbsent(UUID.randomUUID(), year);

        SubscriptionInvoiceCounterEntity counter = this.counterRepository.findByYearForUpdate(year)
                .orElseThrow(() -> new IllegalStateException(
                        "Compteur de factures introuvable pour l'année " + year));

        long sequence = counter.nextSequence();
        this.counterRepository.saveAndFlush(counter);
        return String.format("NL-%d-%04d", year, sequence);
    }
}
