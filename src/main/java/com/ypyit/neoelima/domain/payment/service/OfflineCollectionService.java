package com.ypyit.neoelima.domain.payment.service;

import com.ypyit.neoelima.common.exception.BadRequestException;
import com.ypyit.neoelima.common.exception.NotFoundException;
import com.ypyit.neoelima.common.utils.XofFormat;
import com.ypyit.neoelima.config.security.CurrentUserProvider;
import com.ypyit.neoelima.domain.establishment.entity.InstallmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentEntity;
import com.ypyit.neoelima.domain.establishment.enums.AuditAction;
import com.ypyit.neoelima.domain.establishment.enums.InstallmentStatus;
import com.ypyit.neoelima.domain.establishment.repository.InstallmentRepository;
import com.ypyit.neoelima.domain.establishment.service.AuditService;
import com.ypyit.neoelima.domain.payment.entity.PaymentIntentEntity;
import com.ypyit.neoelima.domain.payment.entity.ReceiptEntity;
import com.ypyit.neoelima.domain.payment.enums.PaymentChannel;
import com.ypyit.neoelima.domain.payment.enums.PaymentIntentStatus;
import com.ypyit.neoelima.domain.payment.form.OfflineCollectionForm;
import com.ypyit.neoelima.domain.payment.repository.PaymentIntentRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Encaissement hors ligne : espèces, chèque, virement reçus par l'école.
 *
 * <p>Ces règlements ne transitent pas par l'agrégateur mais doivent apparaître dans le journal de
 * l'établissement au même titre que les paiements en ligne, sans quoi le rapprochement de caisse
 * est impossible. Ils produisent donc une tentative de paiement et un reçu numéroté comme les
 * autres, avec pour seule différence l'absence de référence agrégateur.
 *
 * <p>Aucune commission YPYit n'est prélevée : elle ne porte que sur les encaissements en ligne.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OfflineCollectionService {

    private final InstallmentRepository installmentRepository;
    private final PaymentIntentRepository paymentIntentRepository;
    private final ReceiptIssuer receiptIssuer;
    private final AuditService auditService;
    private final CurrentUserProvider currentUserProvider;

    @Transactional
    public ReceiptEntity collect(OfflineCollectionForm form) {
        if (PaymentChannel.ONLINE.equals(form.getChannel())) {
            throw new BadRequestException("Ce canal n'est pas un canal d'encaissement hors ligne.");
        }

        InstallmentEntity installment = this.installmentRepository.findById(form.getInstallmentId())
                .orElseThrow(() -> new NotFoundException(String.format(
                        "Installment with provided id %s not found", form.getInstallmentId())));

        this.assertCallerRunsTheSchool(installment);

        // Une tranche est indivisible et ne s'encaisse qu'une fois. Sans ce garde-fou, une double
        // saisie au guichet produirait deux reçus pour un seul règlement.
        if (!InstallmentStatus.PENDING.equals(installment.getStatus())) {
            throw new BadRequestException(String.format(
                    "Cette tranche n'est plus en attente de règlement.", installment.getId(), installment.getStatus()));
        }

        Instant settledAt = Instant.now();
        PaymentIntentEntity paymentIntent = this.paymentIntentRepository.saveAndFlush(
                PaymentIntentEntity.builder()
                        .installment(installment)
                        .payer(this.currentUserProvider.currentUser())
                        .amountSchool(installment.getAmount())
                        .amountCommission(BigDecimal.ZERO)
                        .channel(form.getChannel())
                        .status(PaymentIntentStatus.SUCCEEDED)
                        .providerType(form.getReference())
                        .payerName(StringUtils.trimToNull(form.getPayerName()))
                        .payerEmail(StringUtils.trimToNull(form.getPayerEmail()))
                        .settledAt(settledAt)
                        .build());

        installment.setStatus(InstallmentStatus.PAID);
        installment.setPaidAt(settledAt);
        installment.setPaymentId(paymentIntent.getId());
        this.installmentRepository.saveAndFlush(installment);

        ReceiptEntity receipt = this.receiptIssuer.issueFor(paymentIntent);
        StudentEntity student = installment.getStudentFee().getStudent();
        // De l'argent liquide est entré sur la foi d'un agent : c'est l'acte que le journal doit
        // consigner avant tout autre.
        this.auditService.record(
                Objects.isNull(student.getEstablishment()) ? null : student.getEstablishment().getId(),
                AuditAction.PAYMENT_COLLECTED,
                String.format("Reçu n° %s — élève %s", receipt.getNumber(),
                        Objects.toString(student.getRegistrationNumber(), "—")),
                String.format("%s encaissés en %s", XofFormat.format(installment.getAmount()),
                        form.getChannel()));

        log.info("OFFLINE_COLLECTION: installment {} settled via {}, receipt {}",
                installment.getId(), form.getChannel(), receipt.getNumber());
        return receipt;
    }

    /** Seul un membre de l'établissement de l'élève encaisse à son guichet. */
    private void assertCallerRunsTheSchool(InstallmentEntity installment) {
        StudentEntity student = installment.getStudentFee().getStudent();
        UUID studentEstablishment = Objects.isNull(student.getEstablishment())
                ? null : student.getEstablishment().getId();
        UUID scope = this.currentUserProvider.resolveEstablishmentScope(studentEstablishment);
        if (Objects.nonNull(scope) && !scope.equals(studentEstablishment)) {
            throw new AccessDeniedException(
                    "Installment " + installment.getId() + " belongs to another establishment");
        }
    }
}
