package com.ypyit.neoelima.domain.establishment.controller;

import com.ypyit.neoelima.domain.establishment.dto.NotificationPreferenceDto;
import com.ypyit.neoelima.domain.establishment.form.NotificationPreferenceForm;
import com.ypyit.neoelima.domain.establishment.service.NotificationPreferenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Ce que l'école accepte d'envoyer à ses familles.
 *
 * <p>Le réglage est consulté à l'envoi, dans {@code ReminderDispatcher} et
 * {@code ReceiptPushNotifier} : couper une case coupe réellement le canal.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/notification-preferences")
@Tag(name = "Notifications", description = "Matrice événement × canal de l'établissement")
public class NotificationPreferenceController {

    private final NotificationPreferenceService notificationPreferenceService;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('settings:read')")
    @Operation(summary = "Matrice des notifications",
            description = "Un bloc par événement, une case par canal raccordé. Les canaux non "
                    + "raccordés n'y figurent pas, et les canaux verrouillés portent leur raison.")
    public ResponseEntity<List<NotificationPreferenceDto>> findAll() {
        return ResponseEntity.ok(this.notificationPreferenceService.findAll());
    }

    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('settings:write')")
    @Operation(summary = "Ouvre ou coupe un canal pour un événement",
            description = "Refusé pour le courriel du reçu : il porte une pièce comptable.")
    public ResponseEntity<List<NotificationPreferenceDto>> update(
            @RequestBody @Valid NotificationPreferenceForm form) {
        return ResponseEntity.ok(this.notificationPreferenceService.update(form));
    }
}
