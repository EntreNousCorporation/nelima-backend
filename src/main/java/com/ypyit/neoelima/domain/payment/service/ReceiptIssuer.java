package com.ypyit.neoelima.domain.payment.service;

import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.InstallmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentEntity;
import com.ypyit.neoelima.domain.payment.entity.PaymentIntentEntity;
import com.ypyit.neoelima.domain.payment.entity.ReceiptEntity;
import com.ypyit.neoelima.domain.payment.repository.ReceiptRepository;
import com.ypyit.neoelima.domain.user.entity.UserEntity;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;

/**
 * Émet le reçu d'un encaissement, en ligne comme au guichet.
 *
 * <p>L'opération est idempotente : rappelée pour une tentative déjà quittancée, elle rend le reçu
 * existant au lieu d'en créer un second. C'est indispensable côté paiement en ligne, où les
 * agrégateurs rejouent leurs webhooks.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReceiptIssuer {

    private final ReceiptRepository receiptRepository;
    private final ReceiptNumberAllocator receiptNumberAllocator;
    private final ReceiptMailer receiptMailer;

    @Transactional(propagation = Propagation.MANDATORY)
    public ReceiptEntity issueFor(PaymentIntentEntity paymentIntent) {
        return this.receiptRepository.findByPaymentIntent_Id(paymentIntent.getId())
                .orElseGet(() -> this.create(paymentIntent));
    }

    private ReceiptEntity create(PaymentIntentEntity paymentIntent) {
        InstallmentEntity installment = paymentIntent.getInstallment();
        StudentEntity student = installment.getStudentFee().getStudent();
        EstablishmentEntity establishment = student.getEstablishment();

        Instant issuedAt = Instant.now();
        long sequence = this.receiptNumberAllocator.allocate(establishment);
        String number = this.receiptNumberAllocator
                .format(issuedAt.atZone(ZoneOffset.UTC).getYear(), sequence);

        ReceiptEntity receipt = this.receiptRepository.saveAndFlush(ReceiptEntity.builder()
                .establishment(establishment)
                .paymentIntent(paymentIntent)
                .sequenceNumber(sequence)
                .number(number)
                .amount(paymentIntent.totalAmount())
                .issuedAt(issuedAt)
                .studentLabel(fullNameOf(student))
                .studentRegistrationNumber(student.getRegistrationNumber())
                // Le payeur déclaré au comptoir primes sur l'utilisateur qui a saisi :
                // c'est la famille qui doit apparaître sur sa pièce comptable.
                .payerLabel(StringUtils.isNotBlank(paymentIntent.getPayerName())
                        ? paymentIntent.getPayerName().trim()
                        : fullNameOf(paymentIntent.getPayer()))
                .build());

        log.info("RECEIPT_ISSUED: receipt {} for installment {} of establishment {}",
                number, installment.getId(), establishment.getId());

        // L'envoi est déclenché ici et non par l'appelant : tout encaissement doit être
        // quittancé au payeur, quel que soit le canal, et le déléguer laisserait le choix
        // à chaque chemin d'appel. L'envoi lui-même est asynchrone et tolère l'échec.
        this.receiptMailer.send(receipt);
        return receipt;
    }

    private static String fullNameOf(StudentEntity student) {
        return String.join(" ", nullSafe(student.getFirstName()), nullSafe(student.getLastName())).trim();
    }

    private static String fullNameOf(UserEntity user) {
        if (Objects.isNull(user)) {
            return null;
        }
        return String.join(" ", nullSafe(user.getFirstName()), nullSafe(user.getLastName())).trim();
    }

    private static String nullSafe(String value) {
        return Objects.isNull(value) ? "" : value;
    }
}
