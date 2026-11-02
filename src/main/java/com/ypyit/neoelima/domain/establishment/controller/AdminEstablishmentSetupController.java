package com.ypyit.neoelima.domain.establishment.controller;

import com.ypyit.neoelima.domain.establishment.dto.EstablishmentSetupDto;
import com.ypyit.neoelima.domain.establishment.service.EstablishmentSetupService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Avancement de la configuration d'une école, sur la fiche école de la console YPYit.
 *
 * <p>Réservé à l'équipe plateforme par le filtre de sécurité
 * ({@code SecurityUtils.PLATFORM_CONSOLE_RESOURCES}), comme {@code /admin/receipts} : le rôle admin
 * ne porte aucune permission, une annotation {@code @PreAuthorize} le refuserait à tort.
 */
@RestController
@RequiredArgsConstructor
public class AdminEstablishmentSetupController {

    private final EstablishmentSetupService establishmentSetupService;

    @GetMapping(value = "/admin/establishments/{id}/setup", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Avancement de la configuration d'une école (back-office YPYit)",
            description = "Étapes essentielles, avec leur progression, et modules complémentaires déjà paramétrés.")
    public ResponseEntity<EstablishmentSetupDto> setup(@PathVariable UUID id) {
        return ResponseEntity.ok(this.establishmentSetupService.setupOf(id));
    }
}
