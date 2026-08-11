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
import java.util.UUID;

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
     * Solde la tranche désignée par son identifiant — pour les appelants qui n'ont pas de session.
     *
     * <p>La réconciliation charge ses tentatives, appelle l'agrégateur, puis règle. Entre le
     * chargement et le règlement il n'y a <strong>aucune transaction</strong> — c'est délibéré, un
     * aller-retour HTTP ne doit pas tenir une connexion à la base — mais cela rend les entités
     * <strong>détachées</strong>. {@code settle(entity, …)} déréférence pourtant
     * {@code intent.getInstallment()} : sur une entité détachée, ce proxy porte une session close.
     *
     * <p>Cela fonctionnait par un effet de bord de la configuration : {@code
     * hibernate.enable_lazy_load_no_trans} ouvre une session jetable à chaque accès. Le jour où ce
     * drapeau tombe — et il doit tomber — le rattrapage lève, dans la branche qui solde. C'est-à-dire
     * que <strong>l'argent serait pris et la tranche jamais soldée</strong>, sur le chemin même qui
     * existe pour réparer les webhooks perdus.
     *
     * <p>Relire ici, dans la transaction du règlement, rend la question sans objet : l'entité est
     * gérée, et elle l'est parce que la méthode qui la lit est celle qui l'écrit.
     */
    @Transactional
    public Optional<ReceiptEntity> settle(UUID intentId, String origin) {
        return this.reread(intentId)
                .map(intent -> this.settle(intent, origin))
                .orElseGet(() -> {
                    log.warn("PAYMENT_INTENT_VANISHED: intent {} not found at settlement ({})",
                            intentId, origin);
                    return Optional.empty();
                });
    }

    /** Même raison que {@link #settle(UUID, String)} : relire avant de clore. */
    @Transactional
    public void close(UUID intentId, PaymentIntentStatus outcome, String origin) {
        this.reread(intentId).ifPresentOrElse(
                intent -> this.close(intent, outcome, origin),
                () -> log.warn("PAYMENT_INTENT_VANISHED: intent {} not found at closing ({})",
                        intentId, origin));
    }

    private Optional<PaymentIntentEntity> reread(UUID intentId) {
        return this.paymentIntentRepository.findById(intentId);
    }

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
