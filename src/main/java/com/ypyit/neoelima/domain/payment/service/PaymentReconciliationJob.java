package com.ypyit.neoelima.domain.payment.service;

import com.ypy.paygw.payswitch.api.PaymentService;
import com.ypy.paygw.payswitch.api.PaymentStatus;
import com.ypy.paygw.payswitch.api.UnifiedTransaction;
import com.ypyit.neoelima.domain.payment.entity.PaymentIntentEntity;
import com.ypyit.neoelima.domain.payment.enums.PaymentIntentStatus;
import com.ypyit.neoelima.domain.payment.repository.PaymentIntentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Rattrape les tentatives de paiement restées en attente.
 *
 * <p>Un webhook peut se perdre : indisponibilité passagère du serveur, coupure réseau, ou
 * bascule de fournisseur pendant qu'une transaction était en vol — PaySwitch rejette alors les
 * notifications du fournisseur désactivé. Sans rattrapage, un parent aurait été débité sans que
 * l'école voie l'encaissement, et la tranche resterait affichée comme due.
 *
 * <p>PaySwitch n'expose pas de relecture forcée auprès de l'agrégateur ; on relit donc l'état
 * qu'il a persisté, alimenté par les notifications qu'il a bien reçues. Cela rattrape les cas où
 * PaySwitch a enregistré la transaction mais où le traitement côté Nelima a échoué.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentReconciliationJob {

    private static final String ORIGIN = "reconciliation";

    /**
     * En deçà, la transaction est probablement encore en cours côté parent : il ouvre le tunnel,
     * saisit son code opérateur, valide. Rattraper trop tôt ne servirait à rien.
     */
    private static final Duration PENDING_GRACE_PERIOD = Duration.ofMinutes(15);

    private final PaymentIntentRepository paymentIntentRepository;
    private final PaymentService paymentService;
    private final PaymentSettlementService settlementService;

    @Scheduled(fixedDelayString = "${nelima.billing.reconciliation-interval:PT5M}")
    public void reconcilePendingPayments() {
        Instant threshold = Instant.now().minus(PENDING_GRACE_PERIOD);
        List<PaymentIntentEntity> stale = this.paymentIntentRepository
                .findByStatusAndCreatedAtBefore(PaymentIntentStatus.PENDING, threshold);

        if (stale.isEmpty()) {
            return;
        }
        log.info("RECONCILIATION_START: {} pending payment(s) older than {} minutes",
                stale.size(), PENDING_GRACE_PERIOD.toMinutes());

        // On ne retient de chaque tentative que ce qu'il faut pour interroger l'agrégateur : un
        // identifiant, une référence, une date. **Pas l'entité.**
        //
        // <p>Le dépôt ci-dessus ouvre et referme sa propre transaction : tout ce qu'il rend est
        // détaché à l'instant où il rend. Promener ces entités jusqu'au règlement, c'est déréférencer
        // des associations sur une session close — ce qui ne tenait que par
        // `hibernate.enable_lazy_load_no_trans`. Un identifiant, lui, ne se détache pas.
        List<StaleAttempt> attempts = stale.stream()
                .map(intent -> new StaleAttempt(
                        intent.getId(), intent.getInternalReference(), intent.getCreatedAt()))
                .toList();

        for (StaleAttempt attempt : attempts) {
            try {
                this.reconcile(attempt);
            } catch (RuntimeException e) {
                // Une tentative illisible ne doit pas empêcher de traiter les suivantes.
                log.error("RECONCILIATION_FAILED: reference {} could not be reconciled: {}",
                        attempt.reference(), e.getMessage());
            }
        }
    }

    /** Ce qu'on emporte d'une tentative en attente : rien qui dépende d'une session ouverte. */
    private record StaleAttempt(UUID id, String reference, Instant createdAt) {
    }

    private void reconcile(StaleAttempt attempt) {
        if (StringUtils.isBlank(attempt.reference())) {
            log.warn("RECONCILIATION_SKIPPED: payment intent {} has no aggregator reference",
                    attempt.id());
            return;
        }

        // Hors transaction, et cela reste délibéré : l'agrégateur peut être lent, et tenir une
        // connexion à la base pendant ce temps épuiserait le pool.
        UnifiedTransaction transaction = this.paymentService.get(attempt.reference());
        if (Objects.isNull(transaction)) {
            log.warn("RECONCILIATION_UNKNOWN: reference {} is unknown to PaySwitch",
                    attempt.reference());
            return;
        }

        switch (transaction.status()) {
            case COMPLETED -> this.settlementService.settle(attempt.id(), ORIGIN);
            case FAILED -> this.settlementService.close(attempt.id(), PaymentIntentStatus.FAILED, ORIGIN);
            case CANCELLED, EXPIRED ->
                    this.settlementService.close(attempt.id(), PaymentIntentStatus.CANCELLED, ORIGIN);
            // Un remboursement suppose un encaissement antérieur : le voir sur une tentative encore
            // en attente signale une incohérence, à traiter à la main plutôt qu'à deviner.
            case REFUNDED -> log.error("RECONCILIATION_REFUNDED_WHILE_PENDING: reference {} needs manual review",
                    attempt.reference());
            case PENDING, PROCESSING -> this.warnIfStuck(attempt, transaction.status());
        }
    }

    /**
     * Une transaction encore en cours n'est pas anormale, mais elle ne devrait pas le rester. On
     * n'a pas de moyen de la trancher automatiquement : on la signale pour revue.
     */
    private void warnIfStuck(StaleAttempt attempt, PaymentStatus status) {
        log.warn("RECONCILIATION_STILL_PENDING: reference {} is still {} at the aggregator since {}",
                attempt.reference(), status, attempt.createdAt());
    }
}
