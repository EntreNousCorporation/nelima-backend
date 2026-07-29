package com.ypyit.neoelima.domain.payment.service;

import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.payment.entity.ReceiptCounterEntity;
import com.ypyit.neoelima.domain.payment.repository.ReceiptCounterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Attribue les numéros de reçu, séquentiels par établissement.
 *
 * <p>L'exigence comptable est forte : la suite doit être continue et sans doublon pour un
 * établissement donné. Deux encaissements simultanés dans la même école — le cas courant en début
 * d'année scolaire — ne doivent pas obtenir le même numéro.
 *
 * <p>La sérialisation repose sur un {@code SELECT ... FOR UPDATE} de la ligne de compteur : la
 * seconde transaction attend la première au lieu de lire une valeur périmée. Un simple
 * {@code max(sequence_number) + 1} serait sujet à une course entre la lecture et l'écriture.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReceiptNumberAllocator {

    private final ReceiptCounterRepository receiptCounterRepository;

    /**
     * Réserve le prochain numéro pour cet établissement.
     *
     * <p>Doit être appelée dans la transaction qui persiste le reçu : le verrou n'est relâché qu'à
     * la validation de celle-ci, ce qui empêche une autre transaction de réutiliser le numéro si
     * la première échoue et annule.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public long allocate(EstablishmentEntity establishment) {
        UUID establishmentId = establishment.getId();
        // Garantit la présence de la ligne avant de la verrouiller. L'ordre compte : un
        // SELECT ... FOR UPDATE ne verrouille rien s'il ne trouve pas de ligne, et deux premiers
        // encaissements simultanés dans une école neuve se retrouveraient alors à créer chacun
        // leur compteur.
        this.receiptCounterRepository.insertIfAbsent(UUID.randomUUID(), establishmentId);

        ReceiptCounterEntity counter = this.receiptCounterRepository
                .findByEstablishmentIdForUpdate(establishmentId)
                .orElseThrow(() -> new IllegalStateException(
                        "Receipt counter missing for establishment " + establishmentId));
        long sequence = counter.nextSequence();
        this.receiptCounterRepository.saveAndFlush(counter);
        return sequence;
    }

    /**
     * Numéro présenté au parent. Le rang seul suffirait, mais l'année facilite le classement des
     * pièces côté école.
     */
    public String format(int year, long sequence) {
        return String.format("%d-%06d", year, sequence);
    }
}
