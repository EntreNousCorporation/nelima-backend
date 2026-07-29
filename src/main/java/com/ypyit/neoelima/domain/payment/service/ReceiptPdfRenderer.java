package com.ypyit.neoelima.domain.payment.service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.ypyit.neoelima.domain.payment.entity.PaymentIntentEntity;
import com.ypyit.neoelima.domain.payment.entity.ReceiptEntity;
import com.ypyit.neoelima.domain.payment.enums.PaymentChannel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Rend un reçu en PDF.
 *
 * <p>Le document est décrit par un gabarit Thymeleaf puis converti, plutôt que dessiné par des
 * appels de positionnement : une pièce comptable évolue — mentions légales, coordonnées, logo —
 * et doit rester modifiable sans réécrire une mise en page programmatique.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReceiptPdfRenderer {

    /** Abidjan ne pratique pas l'heure d'été ; l'affichage reste stable toute l'année. */
    private static final ZoneId ABIDJAN = ZoneId.of("Africa/Abidjan");
    private static final DateTimeFormatter ISSUED_AT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH'h'mm");

    private static final Map<PaymentChannel, String> CHANNEL_LABELS = Map.of(
            PaymentChannel.ONLINE, "Paiement en ligne",
            PaymentChannel.CASH, "Espèces",
            PaymentChannel.CHECK, "Chèque",
            PaymentChannel.BANK_TRANSFER, "Virement bancaire");

    private final TemplateEngine templateEngine;

    /**
     * Le rendu traverse les relations du reçu, qui sont paresseuses : l'appel doit donc se faire
     * dans une transaction. Elle est en lecture seule, un reçu ne se modifie pas.
     */
    @Transactional(readOnly = true)
    public byte[] render(ReceiptEntity receipt) {
        Context context = new Context(Locale.FRENCH, this.variablesOf(receipt));
        String xhtml = this.templateEngine.process("receipt-pdf_fr", context);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(xhtml, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception e) {
            // Un reçu illisible est un incident métier, pas un détail technique : l'école en a
            // besoin comme pièce justificative.
            throw new IllegalStateException(
                    "Impossible de générer le PDF du reçu " + receipt.getNumber(), e);
        }
    }

    /** Nom de fichier stable et parlant, utilisé en pièce jointe comme au téléchargement. */
    public String fileNameOf(ReceiptEntity receipt) {
        return "recu-" + receipt.getNumber() + ".pdf";
    }

    private Map<String, Object> variablesOf(ReceiptEntity receipt) {
        PaymentIntentEntity intent = receipt.getPaymentIntent();
        BigDecimal commission = Objects.isNull(intent.getAmountCommission())
                ? BigDecimal.ZERO : intent.getAmountCommission();

        return Map.ofEntries(
                Map.entry("number", receipt.getNumber()),
                Map.entry("establishmentName", nullSafe(receipt.getEstablishment().getName())),
                Map.entry("issuedAt", ISSUED_AT.format(receipt.getIssuedAt().atZone(ABIDJAN))),
                Map.entry("studentLabel", nullSafe(receipt.getStudentLabel())),
                Map.entry("studentRegistrationNumber", nullSafe(receipt.getStudentRegistrationNumber())),
                Map.entry("payerLabel", nullSafe(receipt.getPayerLabel())),
                Map.entry("channelLabel", CHANNEL_LABELS.getOrDefault(intent.getChannel(), "—")),
                Map.entry("installmentLabel", nullSafe(intent.getInstallment().getLabel())),
                Map.entry("amountSchool", formatXof(intent.getAmountSchool())),
                Map.entry("amountCommission", formatXof(commission)),
                Map.entry("amountTotal", formatXof(receipt.getAmount())),
                Map.entry("hasCommission", commission.compareTo(BigDecimal.ZERO) > 0),
                Map.entry("platformName", "Nelima"));
    }

    /**
     * Format retenu pour le franc CFA : séparateur de milliers par espace insécable et suffixe
     * FCFA, sans décimale — la devise n'a pas de subdivision.
     */
    private static String formatXof(BigDecimal amount) {
        if (Objects.isNull(amount)) {
            return "0 FCFA";
        }
        return String.format("%,d FCFA", amount.setScale(0, java.math.RoundingMode.HALF_UP).longValue())
                .replace(',', ' ');
    }

    private static String nullSafe(String value) {
        return Objects.isNull(value) ? "" : value;
    }
}
