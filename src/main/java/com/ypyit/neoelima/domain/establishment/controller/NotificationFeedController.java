package com.ypyit.neoelima.domain.establishment.controller;

import com.ypyit.neoelima.domain.establishment.dto.NotificationDto;
import com.ypyit.neoelima.domain.establishment.service.NotificationFeedService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Fil de notifications de l'école.
 *
 * <p>Aucune permission n'est exigée sur la route : le contenu, lui, est filtré permission par
 * permission dans le service — un compte sans droit sur la comptabilité ne voit pas les
 * encaissements à réconcilier. Exiger une permission ici priverait de cloche des rôles qui ont
 * pourtant des choses à traiter.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/notifications")
@Tag(name = "Notifications", description = "Ce que l'école a à traiter")
public class NotificationFeedController {

    private final NotificationFeedService notificationFeedService;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Fil des trente derniers jours",
            description = "Déduit des faits existants : inscriptions venues des familles, "
                    + "encaissements sans reçu, échéances passées sans règlement. Rien n'est stocké.")
    public ResponseEntity<List<NotificationDto>> feed() {
        return ResponseEntity.ok(this.notificationFeedService.feed());
    }

    @PostMapping(value = "/seen", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Marque le fil comme lu",
            description = "Horodaté à l'appel : un fait survenu pendant la lecture reste non lu.")
    public ResponseEntity<Void> markSeen() {
        this.notificationFeedService.markSeen();
        return ResponseEntity.noContent().build();
    }
}
