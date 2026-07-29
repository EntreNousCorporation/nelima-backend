package com.ypyit.neoelima.domain.payment.service;

import com.ypyit.neoelima.common.service.email.enums.EmailTemplateType;
import com.ypyit.neoelima.common.service.email.service.EmailConstants;
import com.ypyit.neoelima.common.service.email.service.EmailService;
import com.ypyit.neoelima.domain.payment.entity.ReceiptEntity;
import com.ypyit.neoelima.domain.user.entity.ContactEntity;
import com.ypyit.neoelima.domain.user.entity.UserEntity;
import com.ypyit.neoelima.domain.user.enums.ContactType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.context.Context;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.Optional;

/**
 * Envoie le reçu au payeur, en pièce jointe PDF.
 *
 * <p>Le destinataire est le payeur, conformément au cahier des charges. Sur un encaissement au
 * guichet, ce payeur est l'agent qui a saisi l'opération : il reçoit donc le reçu et peut le
 * remettre à la famille. Rattacher un parent payeur à un règlement en espèces demanderait de le
 * saisir au comptoir, ce que le formulaire ne prévoit pas aujourd'hui.
 *
 * <p>Un échec d'envoi ne remet jamais en cause l'encaissement : l'argent est encaissé, le reçu
 * est émis et numéroté, et il reste téléchargeable depuis le portail. On journalise et on
 * continue.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReceiptMailer {

    private final EmailService emailService;
    private final ReceiptPdfRenderer receiptPdfRenderer;

    @Transactional(readOnly = true)
    public void send(ReceiptEntity receipt) {
        Optional<String> recipient = emailOf(receipt.getPaymentIntent().getPayer());
        if (recipient.isEmpty()) {
            log.info("RECEIPT_MAIL_SKIPPED: aucun email pour le payeur du reçu {}", receipt.getNumber());
            return;
        }

        try {
            byte[] pdf = this.receiptPdfRenderer.render(receipt);

            Context context = new Context();
            context.setVariable(EmailConstants.EMAIL, recipient.get());
            context.setVariable(EmailConstants.PLATFORM_NAME, "Nelima");
            context.setVariable("receiptNumber", receipt.getNumber());
            context.setVariable("establishmentName", receipt.getEstablishment().getName());
            context.setVariable("studentLabel", receipt.getStudentLabel());
            context.setVariable("studentRegistrationNumber", receipt.getStudentRegistrationNumber());
            context.setVariable("amountTotal", formatXof(receipt.getAmount()));

            this.emailService.sendWithAttachment(context, EmailTemplateType.RECEIPT,
                    this.receiptPdfRenderer.fileNameOf(receipt), pdf, "application/pdf");

            log.info("RECEIPT_MAIL_SENT: reçu {} envoyé à {}", receipt.getNumber(), recipient.get());
        } catch (RuntimeException e) {
            log.error("RECEIPT_MAIL_FAILED: reçu {} non envoyé à {} : {}",
                    receipt.getNumber(), recipient.get(), e.getMessage());
        }
    }

    private static Optional<String> emailOf(UserEntity user) {
        if (Objects.isNull(user)) {
            return Optional.empty();
        }
        return user.getContacts().stream()
                .filter(contact -> ContactType.EMAIL.equals(contact.getType()))
                .map(ContactEntity::getValue)
                .filter(StringUtils::isNotBlank)
                .findFirst();
    }

    private static String formatXof(BigDecimal amount) {
        if (Objects.isNull(amount)) {
            return "0 FCFA";
        }
        return String.format("%,d FCFA", amount.setScale(0, RoundingMode.HALF_UP).longValue())
                .replace(',', ' ');
    }
}
