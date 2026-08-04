package com.ypyit.neoelima.domain.establishment;

import com.ypyit.neoelima.domain.establishment.dto.CalendarEntryDto;
import com.ypyit.neoelima.domain.establishment.enums.CalendarEntryKind;
import com.ypyit.neoelima.domain.establishment.service.CalendarIcsWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Écriture du fichier iCalendar.
 *
 * <p>Aucune base ici : c'est du texte, et ce sont ses détails qui décident qu'un agenda ouvre le
 * fichier ou le rejette en bloc. Ces cas les fixent un par un, parce qu'aucun ne se remarque avant
 * l'import — et qu'un import raté ne dit jamais pourquoi.
 */
class CalendarIcsWriterTest {

    private static final Instant NOW = Instant.parse("2026-08-04T10:15:30Z");

    private final CalendarIcsWriter writer = new CalendarIcsWriter();

    @Test
    @DisplayName("un événement sur la journée finit le lendemain")
    void allDayEventEndsTheNextDay() {
        String ics = this.writer.write(List.of(this.allDay("Journée portes ouvertes",
                LocalDate.of(2026, 9, 12))), "Nelima", NOW);

        // La date de fin est exclusive : sans le lendemain, l'événement n'apparaît dans aucun
        // agenda, et rien ne signale l'erreur.
        assertThat(ics).contains("DTSTART;VALUE=DATE:20260912");
        assertThat(ics).contains("DTEND;VALUE=DATE:20260913");
    }

    @Test
    @DisplayName("les horaires sont écrits en UTC depuis le fuseau de l'école")
    void writesTimesInUtc() {
        CalendarEntryDto entry = CalendarEntryDto.builder()
                .id("evt-1").kind(CalendarEntryKind.SCHOOL_LIFE).title("Conseil de classe")
                .date(LocalDate.of(2026, 9, 12))
                .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(10, 0))
                .allDay(false).build();

        String ics = this.writer.write(List.of(entry), "Nelima", NOW);

        // Abidjan est à UTC toute l'année : 8 h locales s'écrivent 08:00:00Z.
        assertThat(ics).contains("DTSTART:20260912T080000Z");
        assertThat(ics).contains("DTEND:20260912T100000Z");
    }

    @Test
    @DisplayName("un titre à virgules et points-virgules est échappé")
    void escapesReservedCharacters() {
        String ics = this.writer.write(List.of(this.allDay("Réunion 6e, 5e ; salle B",
                LocalDate.of(2026, 9, 12))), "Nelima", NOW);

        // Sans échappement, la virgule coupe la ligne et l'agenda rejette le fichier entier.
        assertThat(ics).contains("SUMMARY:Réunion 6e\\, 5e \\; salle B");
    }

    @Test
    @DisplayName("une échéance est annoncée comme telle")
    void labelsFeeDueEntries() {
        CalendarEntryDto entry = CalendarEntryDto.builder()
                .id("fee-1@2026-10-15").kind(CalendarEntryKind.FEE_DUE).title("Scolarité T1")
                .date(LocalDate.of(2026, 10, 15)).allDay(true).build();

        String ics = this.writer.write(List.of(entry), "Nelima", NOW);

        assertThat(ics).contains("SUMMARY:Échéance — Scolarité T1");
    }

    @Test
    @DisplayName("chaque entrée porte un identifiant stable")
    void usesStableIdentifiers() {
        List<CalendarEntryDto> entries = List.of(
                this.allDay("Rentrée", LocalDate.of(2026, 9, 12)),
                this.allDay("Conseil", LocalDate.of(2026, 9, 14)));

        String first = this.writer.write(entries, "Nelima", NOW);
        String second = this.writer.write(entries, "Nelima", NOW.plusSeconds(3600));

        // Un UID aléatoire ferait qu'un second import duplique tout le calendrier au lieu de le
        // mettre à jour.
        assertThat(uids(first)).isEqualTo(uids(second));
        assertThat(uids(first)).hasSize(2);
    }

    @Test
    @DisplayName("l'enveloppe est complète et les lignes finissent en CRLF")
    void writesAWellFormedEnvelope() {
        String ics = this.writer.write(List.of(this.allDay("Rentrée", LocalDate.of(2026, 9, 12))),
                "Nelima", NOW);

        assertThat(ics).startsWith("BEGIN:VCALENDAR\r\n");
        assertThat(ics).endsWith("END:VCALENDAR\r\n");
        assertThat(ics).contains("VERSION:2.0");
        assertThat(ics).contains("BEGIN:VEVENT").contains("END:VEVENT");
        assertThat(ics).contains("DTSTAMP:20260804T101530Z");
    }

    private CalendarEntryDto allDay(String title, LocalDate date) {
        return CalendarEntryDto.builder()
                .id("evt-" + title.hashCode()).kind(CalendarEntryKind.SCHOOL_LIFE)
                .title(title).date(date).allDay(true).build();
    }

    private static List<String> uids(String ics) {
        return ics.lines().filter(line -> line.startsWith("UID:")).toList();
    }
}
