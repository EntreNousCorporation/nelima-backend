package com.ypyit.neoelima.domain.payment.service;

import com.ypyit.neoelima.domain.payment.entity.ReceiptEntity;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/**
 * Journal des encaissements au format tableur.
 *
 * <p>Destiné au comptable de l'école, qui travaille sous Excel en français : le séparateur est le
 * point-virgule, la virgule y étant le séparateur décimal, et le fichier commence par une marque
 * d'ordre d'octets sans laquelle Excel lit l'UTF-8 comme du latin-1 et affiche « KouassiÃ© ».
 */
@Service
public class ReceiptCsvExporter {

    private static final ZoneId ABIDJAN = ZoneId.of("Africa/Abidjan");
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ABIDJAN);

    private static final String SEPARATOR = ";";
    private static final byte[] BYTE_ORDER_MARK = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    public byte[] export(List<ReceiptEntity> receipts) {
        StringBuilder csv = new StringBuilder();
        csv.append(String.join(SEPARATOR,
                "Numero", "Date", "Eleve", "Matricule", "Payeur", "Mode", "Montant")).append('\n');

        for (ReceiptEntity receipt : receipts) {
            csv.append(String.join(SEPARATOR,
                    escape(receipt.getNumber()),
                    escape(DATE_TIME.format(receipt.getIssuedAt())),
                    escape(receipt.getStudentLabel()),
                    escape(receipt.getStudentRegistrationNumber()),
                    escape(receipt.getPayerLabel()),
                    escape(label(receipt)),
                    // Montant sans séparateur de milliers et avec la virgule décimale : c'est la
                    // seule forme qu'un tableur français additionne sans qu'on la retouche.
                    escape(receipt.getAmount().toPlainString().replace('.', ','))
            )).append('\n');
        }

        byte[] body = csv.toString().getBytes(StandardCharsets.UTF_8);
        byte[] withMark = new byte[BYTE_ORDER_MARK.length + body.length];
        System.arraycopy(BYTE_ORDER_MARK, 0, withMark, 0, BYTE_ORDER_MARK.length);
        System.arraycopy(body, 0, withMark, BYTE_ORDER_MARK.length, body.length);
        return withMark;
    }

    private static String label(ReceiptEntity receipt) {
        if (Objects.isNull(receipt.getChannel())) {
            return "";
        }
        return switch (receipt.getChannel()) {
            case ONLINE -> "En ligne";
            case CASH -> "Especes";
            case CHECK -> "Cheque";
            case BANK_TRANSFER -> "Virement";
        };
    }

    /**
     * Un nom peut contenir un point-virgule ou un guillemet, et le laisser passer tel quel
     * décalerait toutes les colonnes de la ligne — un journal comptable illisible.
     */
    private static String escape(String value) {
        if (Objects.isNull(value) || value.isBlank()) {
            return "";
        }
        String cleaned = value.replace("\"", "\"\"");
        return cleaned.contains(SEPARATOR) || cleaned.contains("\"") || cleaned.contains("\n")
                ? "\"" + cleaned + "\""
                : cleaned;
    }
}
