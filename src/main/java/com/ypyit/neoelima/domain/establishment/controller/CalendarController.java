package com.ypyit.neoelima.domain.establishment.controller;

import com.ypyit.neoelima.domain.establishment.dto.CalendarEntryDto;
import com.ypyit.neoelima.domain.establishment.form.SchoolEventForm;
import com.ypyit.neoelima.domain.establishment.service.CalendarIcsWriter;
import com.ypyit.neoelima.domain.establishment.service.CalendarService;
import com.ypyit.neoelima.domain.establishment.service.SchoolEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Calendrier scolaire.
 *
 * <p>Deux publics, et la distinction est vitale : les routes du portail école exigent une
 * permission, celle de l'application parent n'en exige <em>aucune</em> — le rôle parent n'en porte
 * pas, son accès se vérifie enfant par enfant. {@code ParentRouteOpennessTest} le verrouille.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/calendar")
@Tag(name = "Calendrier", description = "Vie scolaire, examens et échéances financières")
public class CalendarController {

    private final CalendarService calendarService;
    private final SchoolEventService schoolEventService;
    private final CalendarIcsWriter icsWriter;

    /* ---------- Portail école ---------- */

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('calendar:read')")
    @Operation(summary = "Entrées du calendrier sur une période",
            description = "Événements saisis et échéances déduites des tranches dues. Les montants "
                    + "ne sont servis qu'aux appelants portant fee:read.")
    public ResponseEntity<List<CalendarEntryDto>> findAll(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(this.calendarService.findAll(from, to));
    }

    @GetMapping(value = "/export", produces = "text/calendar")
    @PreAuthorize("hasAuthority('calendar:read')")
    @Operation(summary = "Exporte la période au format iCalendar",
            description = "Fichier .ics ouvrable dans Google Agenda, Outlook ou Apple Calendrier.")
    public ResponseEntity<byte[]> export(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        String ics = this.icsWriter.write(this.calendarService.findAll(from, to),
                "Nelima — calendrier scolaire", Instant.now());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        String.format("attachment; filename=\"calendrier-%s-%s.ics\"", from, to))
                .contentType(MediaType.parseMediaType("text/calendar; charset=UTF-8"))
                .body(ics.getBytes(StandardCharsets.UTF_8));
    }

    @PostMapping(value = "/events", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('calendar:write')")
    @Operation(summary = "Crée un événement",
            description = "Rendre un événement visible des familles ne les prévient pas : "
                    + "l'envoi est une action distincte.")
    public ResponseEntity<CalendarEntryDto> create(@RequestBody @Valid SchoolEventForm form) {
        return ResponseEntity.ok(this.schoolEventService.create(form));
    }

    @PutMapping(value = "/events/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('calendar:write')")
    public ResponseEntity<CalendarEntryDto> update(@PathVariable UUID id,
                                                   @RequestBody @Valid SchoolEventForm form) {
        return ResponseEntity.ok(this.schoolEventService.update(id, form));
    }

    @DeleteMapping("/events/{id}")
    @PreAuthorize("hasAuthority('calendar:write')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        this.schoolEventService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/events/{id}/notify", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('calendar:write')")
    @Operation(summary = "Prévient les familles concernées",
            description = "Geste explicite et répétable. Refusé si l'événement n'est pas visible "
                    + "des familles : le parent atterrirait sur un écran vide.")
    public ResponseEntity<Map<String, Integer>> notifyFamilies(@PathVariable UUID id) {
        return ResponseEntity.ok(Map.of("notified", this.schoolEventService.notifyFamilies(id)));
    }

    /* ---------- Application parent — sans exigence de permission ---------- */

    @GetMapping(value = "/mine", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Calendrier des enfants du parent authentifié",
            description = "Événements rendus visibles qui concernent leurs classes, et échéances "
                    + "de leurs propres enfants.")
    public ResponseEntity<List<CalendarEntryDto>> findMine(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(this.calendarService.findMine(from, to));
    }
}
