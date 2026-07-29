package com.ypyit.neoelima.domain.payment.service;

import com.ypyit.neoelima.domain.establishment.entity.InstallmentEntity;
import com.ypyit.neoelima.domain.establishment.enums.InstallmentStatus;
import com.ypyit.neoelima.domain.establishment.repository.InstallmentRepository;
import com.ypyit.neoelima.domain.payment.entity.PaymentIntentEntity;
import com.ypyit.neoelima.domain.payment.entity.ReceiptEntity;
import com.ypyit.neoelima.domain.payment.enums.PaymentIntentStatus;
import com.ypyit.neoelima.domain.payment.repository.PaymentIntentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Applique le sort d'une tentative de paiement, quelle qu'en soit la source.
 *
 * <p>Deux chemins mènent ici : le webhook de l'agrégateur, et la réconciliation qui rattrape les
 * webhooks perdus. Ils doivent aboutir au même état, sans quoi un encaissement réel resterait
 * invisible pour l'école ou serait compté deux fois. La logique est donc tenue en un seul endroit.
 *
 * <p>Toutes les opérations sont idempotentes : rappelées sur une tentative déjà close, elles ne
 * font rien.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentSettlementService {

    private final PaymentIntentRepository paymentIntentRepository;
    private final InstallmentRepository installmentRepository;
    private final ReceiptIssuer receiptIssuer;

    /**
     * Solde la tranche et émet le reçu.
     *
     * @return le reçu, vide si la tentative était déjà traitée ou si la tranche a été soldée
     *         autrement entre-temps
     */
    @Transactional
    public Optional<ReceiptEntity> settle(PaymentIntentEntity intent, String origin) {
        if (!PaymentIntentStatus.PENDING.equals(intent.getStatus())) {
            log.info("PAYMENT_ALREADY_HANDLED: reference {} is already {} ({})",
                    intent.getInternalReference(), intent.getStatus(), origin);
            return Optional.empty();
        }

        Instant settledAt = Instant.now();
        intent.setStatus(PaymentIntentStatus.SUCCEEDED);
        intent.setSettledAt(settledAt);
        this.paymentIntentRepository.saveAndFlush(intent);

        InstallmentEntity installment = intent.getInstallment();
        // La tranche a pu être encaissée au guichet entre-temps : le paiement en ligne est alors un
        // doublon à rembourser, pas un encaissement à enregistrer. On ne l'écrase pas.
        if (!InstallmentStatus.PENDING.equals(installment.getStatus())) {
            log.warn("PAYMENT_ON_SETTLED_INSTALLMENT: installment {} was already {} when reference {} succeeded ({})",
                    installment.getId(), installment.getStatus(), intent.getInternalReference(), origin);
            return Optional.empty();
        }

        installment.setStatus(InstallmentStatus.PAID);
        installment.setPaidAt(settledAt);
        installment.setPaymentId(intent.getId());
        this.installmentRepository.saveAndFlush(installment);

        ReceiptEntity receipt = this.receiptIssuer.issueFor(intent);
        log.info("PAYMENT_SETTLED: reference {} settled installment {}, receipt {} ({})",
                intent.getInternalReference(), installment.getId(), receipt.getNumber(), origin);
        return Optional.of(receipt);
    }

    /**
     * Clôt une tentative sans suite. La tranche reste due : un échec ne la touche jamais, le parent
     * doit pouvoir réessayer.
     */
    @Transactional
    public void close(PaymentIntentEntity intent, PaymentIntentStatus outcome, String origin) {
        if (!PaymentIntentStatus.PENDING.equals(intent.getStatus())) {
            log.info("PAYMENT_ALREADY_HANDLED: reference {} is already {} ({})",
                    intent.getInternalReference(), intent.getStatus(), origin);
            return;
        }
        intent.setStatus(outcome);
        this.paymentIntentRepository.saveAndFlush(intent);
        log.info("PAYMENT_CLOSED: reference {} ended as {} ({})",
                intent.getInternalReference(), outcome, origin);
    }
}
