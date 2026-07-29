package com.ypyit.neoelima.domain.payment.service;

import com.ypy.paygw.payswitch.api.UnifiedTransaction;
import com.ypy.paygw.payswitch.api.event.PaymentCancelledEvent;
import com.ypy.paygw.payswitch.api.event.PaymentFailedEvent;
import com.ypy.paygw.payswitch.api.event.PaymentSucceededEvent;
import com.ypyit.neoelima.domain.payment.entity.PaymentIntentEntity;
import com.ypyit.neoelima.domain.payment.enums.PaymentIntentStatus;
import com.ypyit.neoelima.domain.payment.repository.PaymentIntentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Applique côté Nelima le sort des transactions notifiées par PaySwitch.
 *
 * <p><strong>Tout ici doit être idempotent.</strong> Les agrégateurs rejouent leurs webhooks —
 * Jeko réessaie trois fois sur une vingtaine de minutes dès qu'il n'obtient pas un 200 franc — et
 * PaySwitch republie l'événement à chaque réception. Un traitement naïf solderait deux fois la
 * même tranche et émettrait deux reçus pour un seul encaissement.
 *
 * <p>La décision elle-même est déléguée à {@link PaymentSettlementService}, que partage la
 * réconciliation : les deux chemins doivent aboutir au même état.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventListener {

    private static final String ORIGIN = "webhook";

    private final PaymentIntentRepository paymentIntentRepository;
    private final PaymentSettlementService settlementService;

    @EventListener
    @Transactional
    public void on(PaymentSucceededEvent event) {
        this.locate(event.transaction())
                .ifPresent(intent -> this.settlementService.settle(intent, ORIGIN));
    }

    @EventListener
    @Transactional
    public void on(PaymentFailedEvent event) {
        this.locate(event.transaction())
                .ifPresent(intent -> this.settlementService.close(intent, PaymentIntentStatus.FAILED, ORIGIN));
    }

    @EventListener
    @Transactional
    public void on(PaymentCancelledEvent event) {
        this.locate(event.transaction())
                .ifPresent(intent -> this.settlementService.close(intent, PaymentIntentStatus.CANCELLED, ORIGIN));
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
