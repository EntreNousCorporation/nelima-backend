package com.ypyit.neoelima.domain.establishment.service;

import com.ypyit.neoelima.domain.establishment.dto.CalendarEntryDto;
import com.ypyit.neoelima.domain.establishment.enums.CalendarEntryKind;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/**
 * Écrit un calendrier au format iCalendar (RFC 5545), pour l'ouvrir dans un agenda du commerce.
 *
 * <p>Quatre détails font qu'un fichier ne s'ouvre nulle part, et chacun est traité ici plutôt que
 * découvert à l'usage : la date de fin exclusive d'un événement sur la journée, le fuseau, les
 * caractères à échapper dans un titre, et la stabilité des identifiants.
 */
@Component
public class CalendarIcsWriter {

    /** Fuseau de la Côte d'Ivoire. Les horaires sont saisis dans celui-ci et écrits en UTC. */
    private static final ZoneId SCHOOL_ZONE = ZoneId.of("Africa/Abidjan");

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

    /** Terminaison de ligne imposée par la norme : CRLF, et non le saut de ligne du système. */
    private static final String CRLF = "\r\n";

    public String write(List<CalendarEntryDto> entries, String calendarName, java.time.Instant now) {
        StringBuilder ics = new StringBuilder();
        ics.append("BEGIN:VCALENDAR").append(CRLF);
        ics.append("VERSION:2.0").append(CRLF);
        ics.append("PRODID:-//YPYit//Nelima//FR").append(CRLF);
        ics.append("CALSCALE:GREGORIAN").append(CRLF);
        ics.append("METHOD:PUBLISH").append(CRLF);
        ics.append("X-WR-CALNAME:").append(escape(calendarName)).append(CRLF);
        ics.append("X-WR-TIMEZONE:Africa/Abidjan").append(CRLF);

        String stamp = TIMESTAMP.format(now.atZone(ZoneOffset.UTC));
        for (CalendarEntryDto entry : entries) {
            this.append(ics, entry, stamp);
        }

        ics.append("END:VCALENDAR").append(CRLF);
        return ics.toString();
    }

    private void append(StringBuilder ics, CalendarEntryDto entry, String stamp) {
        ics.append("BEGIN:VEVENT").append(CRLF);
        // Identifiant stable : construit sur celui de l'entrée, il ne change pas d'un export à
        // l'autre. Un UID aléatoire ferait qu'un second import duplique tout le calendrier.
        ics.append("UID:").append(entry.getId()).append("@nelima.ci").append(CRLF);
        ics.append("DTSTAMP:").append(stamp).append(CRLF);

        if (entry.isAllDay() || Objects.isNull(entry.getStartTime())) {
            // La date de fin est exclusive : sans le lendemain, un événement d'un jour n'apparaît
            // dans aucun agenda.
            ics.append("DTSTART;VALUE=DATE:").append(DATE.format(entry.getDate())).append(CRLF);
            ics.append("DTEND;VALUE=DATE:").append(DATE.format(entry.getDate().plusDays(1))).append(CRLF);
        } else {
            ics.append("DTSTART:").append(utc(entry.getDate(), entry.getStartTime())).append(CRLF);
            ics.append("DTEND:").append(utc(entry.getDate(),
                    Objects.requireNonNullElse(entry.getEndTime(), entry.getStartTime().plusHours(1))))
                    .append(CRLF);
        }

        ics.append("SUMMARY:").append(escape(this.summaryOf(entry))).append(CRLF);
        String description = this.descriptionOf(entry);
        if (Objects.nonNull(description)) {
            ics.append("DESCRIPTION:").append(escape(description)).append(CRLF);
        }
        ics.append("END:VEVENT").append(CRLF);
    }

    private String summaryOf(CalendarEntryDto entry) {
        return CalendarEntryKind.FEE_DUE.equals(entry.getKind())
                ? "Échéance — " + entry.getTitle()
                : entry.getTitle();
    }

    private String descriptionOf(CalendarEntryDto entry) {
        StringBuilder text = new StringBuilder();
        if (Objects.nonNull(entry.getDetails())) {
            text.append(entry.getDetails());
        }
        if (Objects.nonNull(entry.getScope())) {
            if (!text.isEmpty()) {
                text.append(" — ");
            }
            text.append(entry.getScope());
        }
        return text.isEmpty() ? null : text.toString();
    }

    private static String utc(LocalDate date, java.time.LocalTime time) {
        return TIMESTAMP.format(LocalDateTime.of(date, time)
                .atZone(SCHOOL_ZONE)
                .withZoneSameInstant(ZoneOffset.UTC));
    }

    /**
     * Échappe ce que la norme réserve.
     *
     * <p>La barre oblique inverse d'abord : l'échapper après les autres échapperait aussi celles
     * qu'on vient d'introduire. Un titre comme « Réunion 6e, 5e ; salle B » casse la ligne sans
     * cela, et l'agenda rejette le fichier entier.
     */
    private static String escape(String value) {
        if (Objects.isNull(value)) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\n", "\\n");
    }
}
