package com.ypyit.neoelima.domain.payment.service;

import com.ypyit.neoelima.common.service.email.enums.EmailTemplateType;
import com.ypyit.neoelima.common.service.email.service.EmailConstants;
import com.ypyit.neoelima.common.service.email.service.EmailService;
import com.ypyit.neoelima.domain.payment.entity.PaymentIntentEntity;
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
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Envoie le reçu en pièce jointe PDF aux personnes concernées par le règlement.
 *
 * <p>Le destinataire naturel d'une notification sur un élève est <strong>son tuteur</strong> : il
 * est rattaché à l'élève dans le système et ses coordonnées y sont tenues à jour. C'est donc lui
 * qui reçoit le reçu, quel que soit le canal de paiement.
 *
 * <p>S'y ajoute la personne qui a effectivement réglé, quand elle est identifiée et distincte :
 * l'email saisi au comptoir pour un règlement en espèces, ou le compte utilisé pour un paiement en
 * ligne — le paiement étant découplé du tutorat, un oncle ou un bienfaiteur peut payer et a droit
 * à sa quittance.
 *
 * <p>Le compte d'un utilisateur d'établissement est <strong>toujours exclu</strong> : l'agent qui
 * saisit une opération au comptoir n'est pas le payeur, et lui adresser la pièce comptable d'une
 * famille exposerait ses données sans raison. Sans aucun destinataire légitime, rien n'est envoyé.
 *
 * <p>Un échec d'envoi ne remet jamais en cause l'encaissement : l'argent est encaissé, le reçu
 * est émis et numéroté, et il reste téléchargeable depuis le portail.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReceiptMailer {

    private final EmailService emailService;
    private final ReceiptPdfRenderer receiptPdfRenderer;

    @Transactional(readOnly = true)
    public void send(ReceiptEntity receipt) {
        Set<String> recipients = this.recipientsOf(receipt);
        if (recipients.isEmpty()) {
            log.warn("RECEIPT_MAIL_SKIPPED: aucun destinataire pour le reçu {} — l'élève {} n'a "
                            + "aucun tuteur joignable et aucun email de payeur n'a été saisi",
                    receipt.getNumber(), receipt.getStudentRegistrationNumber());
            return;
        }

        byte[] pdf;
        try {
            pdf = this.receiptPdfRenderer.render(receipt);
        } catch (RuntimeException e) {
            log.error("RECEIPT_MAIL_FAILED: PDF du reçu {} non généré : {}",
                    receipt.getNumber(), e.getMessage());
            return;
        }

        String fileName = this.receiptPdfRenderer.fileNameOf(receipt);
        for (String recipient : recipients) {
            try {
                this.emailService.sendWithAttachment(this.contextFor(receipt, recipient),
                        EmailTemplateType.RECEIPT, fileName, pdf, "application/pdf");
                log.info("RECEIPT_MAIL_SENT: reçu {} envoyé à {}", receipt.getNumber(), recipient);
            } catch (RuntimeException e) {
                // Un destinataire injoignable ne doit pas priver les autres de leur reçu.
                log.error("RECEIPT_MAIL_FAILED: reçu {} non envoyé à {} : {}",
                        receipt.getNumber(), recipient, e.getMessage());
            }
        }
    }

    private Context contextFor(ReceiptEntity receipt, String recipient) {
        Context context = new Context();
        context.setVariable(EmailConstants.EMAIL, recipient);
        context.setVariable(EmailConstants.PLATFORM_NAME, "Nelima");
        context.setVariable("receiptNumber", receipt.getNumber());
        context.setVariable("establishmentName", receipt.getEstablishment().getName());
        context.setVariable("studentLabel", receipt.getStudentLabel());
        context.setVariable("studentRegistrationNumber", receipt.getStudentRegistrationNumber());
        context.setVariable("amountTotal", formatXof(receipt.getAmount()));
        return context;
    }

    /**
     * Ensemble ordonné et dédoublonné, la comparaison se faisant en minuscules : un même parent
     * saisi au comptoir avec une casse différente de celle de son compte ne doit pas recevoir deux
     * fois le même reçu.
     */
    private Set<String> recipientsOf(ReceiptEntity receipt) {
        PaymentIntentEntity intent = receipt.getPaymentIntent();
        Set<String> recipients = new LinkedHashSet<>();

        // Comptes concernés : la règle vit dans ReceiptAudience, partagée avec la notification push.
        ReceiptAudience.accountsOf(intent).stream()
                .map(ReceiptMailer::emailOf)
                .flatMap(Optional::stream)
                .map(ReceiptMailer::normalize)
                .forEach(recipients::add);

        // L'email saisi au comptoir n'a pas de compte derrière lui : il est propre à ce canal, un
        // destinataire sans compte n'ayant aucun appareil à notifier.
        Optional.ofNullable(intent.getPayerEmail())
                .map(ReceiptMailer::normalize)
                .filter(StringUtils::isNotBlank)
                .ifPresent(recipients::add);

        recipients.remove("");
        return recipients;
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

    private static String normalize(String email) {
        return Objects.isNull(email) ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static String formatXof(BigDecimal amount) {
        if (Objects.isNull(amount)) {
            return "0 FCFA";
        }
        return String.format("%,d FCFA", amount.setScale(0, RoundingMode.HALF_UP).longValue())
                .replace(',', ' ');
    }
}
