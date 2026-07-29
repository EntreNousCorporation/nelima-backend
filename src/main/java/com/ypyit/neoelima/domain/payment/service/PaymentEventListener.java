package com.ypyit.neoelima.domain.payment.service;

import com.ypy.paygw.payswitch.api.UnifiedTransaction;
import com.ypy.paygw.payswitch.api.event.PaymentCancelledEvent;
import com.ypy.paygw.payswitch.api.event.PaymentFailedEvent;
import com.ypy.paygw.payswitch.api.event.PaymentSucceededEvent;
import com.ypyit.neoelima.domain.establishment.entity.InstallmentEntity;
import com.ypyit.neoelima.domain.establishment.enums.InstallmentStatus;
import com.ypyit.neoelima.domain.establishment.repository.InstallmentRepository;
import com.ypyit.neoelima.domain.payment.entity.PaymentIntentEntity;
import com.ypyit.neoelima.domain.payment.entity.ReceiptEntity;
import com.ypyit.neoelima.domain.payment.enums.PaymentIntentStatus;
import com.ypyit.neoelima.domain.payment.repository.PaymentIntentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Applique côté Nelima le sort des transactions notifiées par PaySwitch.
 *
 * <p><strong>Tout ici doit être idempotent.</strong> Les agrégateurs rejouent leurs webhooks —
 * Jeko réessaie trois fois sur une vingtaine de minutes dès qu'il n'obtient pas un 200 franc — et
 * PaySwitch republie l'événement à chaque réception. Un traitement naïf solderait deux fois la
 * même tranche et émettrait deux reçus pour un seul encaissement.
 *
 * <p>La protection est double : la tentative de paiement n'est traitée que si elle est encore en
 * attente, et {@link ReceiptIssuer} rend le reçu déjà émis au lieu d'en créer un second.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventListener {

    private final PaymentIntentRepository paymentIntentRepository;
    private final InstallmentRepository installmentRepository;
    private final ReceiptIssuer receiptIssuer;

    @EventListener
    @Transactional
    public void on(PaymentSucceededEvent event) {
        UnifiedTransaction transaction = event.transaction();
        Optional<PaymentIntentEntity> maybeIntent = this.locate(transaction);
        if (maybeIntent.isEmpty()) {
            return;
        }
        PaymentIntentEntity intent = maybeIntent.get();

        if (!PaymentIntentStatus.PENDING.equals(intent.getStatus())) {
            log.info("PAYMENT_REPLAY_IGNORED: reference {} is already {}",
                    transaction.internalReference(), intent.getStatus());
            return;
        }

        Instant settledAt = Instant.now();
        intent.setStatus(PaymentIntentStatus.SUCCEEDED);
        intent.setSettledAt(settledAt);
        this.paymentIntentRepository.saveAndFlush(intent);

        InstallmentEntity installment = intent.getInstallment();
        // La tranche a pu être encaissée au guichet entre-temps : le paiement en ligne est alors
        // un doublon à rembourser, pas un encaissement à enregistrer. On ne l'écrase pas.
        if (!InstallmentStatus.PENDING.equals(installment.getStatus())) {
            log.warn("PAYMENT_ON_SETTLED_INSTALLMENT: installment {} was already {} when reference {} succeeded",
                    installment.getId(), installment.getStatus(), transaction.internalReference());
            return;
        }

        installment.setStatus(InstallmentStatus.PAID);
        installment.setPaidAt(settledAt);
        installment.setPaymentId(intent.getId());
        this.installmentRepository.saveAndFlush(installment);

        ReceiptEntity receipt = this.receiptIssuer.issueFor(intent);
        log.info("PAYMENT_SUCCEEDED: reference {} settled installment {}, receipt {}",
                transaction.internalReference(), installment.getId(), receipt.getNumber());
    }

    @EventListener
    @Transactional
    public void on(PaymentFailedEvent event) {
        this.close(event.transaction(), PaymentIntentStatus.FAILED);
    }

    @EventListener
    @Transactional
    public void on(PaymentCancelledEvent event) {
        this.close(event.transaction(), PaymentIntentStatus.CANCELLED);
    }

    /**
     * Clôt une tentative sans suite. La tranche reste due : elle n'est jamais touchée par un échec,
     * le parent doit pouvoir réessayer.
     */
    private void close(UnifiedTransaction transaction, PaymentIntentStatus outcome) {
        this.locate(transaction).ifPresent(intent -> {
            if (!PaymentIntentStatus.PENDING.equals(intent.getStatus())) {
                log.info("PAYMENT_REPLAY_IGNORED: reference {} is already {}",
                        transaction.internalReference(), intent.getStatus());
                return;
            }
            intent.setStatus(outcome);
            this.paymentIntentRepository.saveAndFlush(intent);
            log.info("PAYMENT_CLOSED: reference {} ended as {}", transaction.internalReference(), outcome);
        });
    }

    /**
     * Une transaction PaySwitch sans tentative Nelima correspondante n'est pas anodine : c'est soit
     * une transaction émise par une autre application partageant l'agrégateur, soit une incohérence
     * de données. On la trace sans la traiter plutôt que d'échouer, pour que le webhook reçoive un
     * 200 et que l'agrégateur cesse de réessayer.
     */
    private Optional<PaymentIntentEntity> locate(UnifiedTransaction transaction) {
        Optional<PaymentIntentEntity> intent =
                this.paymentIntentRepository.findByInternalReference(transaction.internalReference());
        if (intent.isEmpty()) {
            log.warn("PAYMENT_INTENT_NOT_FOUND: no Nelima payment intent for reference {}",
                    transaction.internalReference());
        }
        return intent;
    }
}
