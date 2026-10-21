package com.ypyit.neoelima.domain.establishment.service;

import com.ypyit.neoelima.domain.establishment.dto.StudentDto;
import com.ypyit.neoelima.domain.establishment.enums.StudentPaymentStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/**
 * Liste des élèves au format tableur, telle que l'écran la filtre.
 *
 * <p>Même convention que le journal des encaissements : point-virgule et marque d'ordre d'octets,
 * pour qu'Excel en français l'ouvre en colonnes et avec les accents.
 */
@Service
public class StudentCsvExporter {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String SEPARATOR = ";";
    private static final byte[] BYTE_ORDER_MARK = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    public byte[] export(List<StudentDto> students) {
        StringBuilder csv = new StringBuilder();
        csv.append(String.join(SEPARATOR, "Matricule", "Nom", "Prenom", "Date de naissance",
                "Sexe", "Niveau", "Classe", "Statut paiement", "Solde", "Dossier")).append('\n');
        for (StudentDto student : students) {
            csv.append(String.join(SEPARATOR,
                    escape(student.getRegistrationNumber()),
                    escape(student.getLastName()),
                    escape(student.getFirstName()),
                    escape(Objects.isNull(student.getBirthDay()) ? null : DATE.format(student.getBirthDay())),
                    escape(Objects.isNull(student.getGender()) ? null
                            : switch (student.getGender()) { case FEMALE -> "F"; case MALE -> "M"; }),
                    escape(Objects.isNull(student.getLevelOfStudy()) ? null : student.getLevelOfStudy().getCode()),
                    escape(Objects.isNull(student.getSchoolClass()) ? null : student.getSchoolClass().getName()),
                    escape(label(student.getPaymentStatus())),
                    // Vide, et non zéro, quand le solde n'est pas visible pour ce profil.
                    escape(Objects.isNull(student.getOutstandingAmount()) ? null
                            : student.getOutstandingAmount().toPlainString().replace('.', ',')),
                    escape(Boolean.TRUE.equals(student.getDossierComplete()) ? "Complet" : "Incomplet")
            )).append('\n');
        }
        byte[] body = csv.toString().getBytes(StandardCharsets.UTF_8);
        byte[] withMark = new byte[BYTE_ORDER_MARK.length + body.length];
        System.arraycopy(BYTE_ORDER_MARK, 0, withMark, 0, BYTE_ORDER_MARK.length);
        System.arraycopy(body, 0, withMark, BYTE_ORDER_MARK.length, body.length);
        return withMark;
    }

    private static String label(StudentPaymentStatus status) {
        if (Objects.isNull(status)) {
            return "";
        }
        return switch (status) {
            case UP_TO_DATE -> "A jour";
            case DEPOSIT -> "Acompte";
            case OVERDUE -> "En retard";
        };
    }

    /**
     * Une cellule qui commence par =, +, - ou @ serait lue comme une formule par le tableur : un nom
     * saisi « =HYPERLINK(...) » s'exécuterait à l'ouverture chez le comptable. On la neutralise.
     */
    private static String escape(String value) {
        if (Objects.isNull(value) || value.isBlank()) {
            return "";
        }
        String cleaned = value.replace("\"", "\"\"");
        if ("=+-@".indexOf(cleaned.charAt(0)) >= 0 && !cleaned.matches("-?\\d+(,\\d+)?")) {
            cleaned = "'" + cleaned;
        }
        return cleaned.contains(SEPARATOR) || cleaned.contains("\"") || cleaned.contains("\n")
                ? "\"" + cleaned + "\""
                : cleaned;
    }
}
