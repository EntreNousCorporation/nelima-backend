package com.ypyit.neoelima.domain.establishment.controller;

import com.ypyit.neoelima.domain.establishment.dto.AuditEventDto;
import com.ypyit.neoelima.domain.establishment.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Journal d'audit, en lecture seule.
 *
 * <p>Permission dédiée : savoir ce que font ses collègues n'est pas un droit ordinaire de
 * l'établissement, c'est celui de la direction.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/audit")
@Tag(name = "Journal d'audit", description = "Actes sensibles, par période et par auteur")
public class AuditController {

    private final AuditService auditService;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('audit:read')")
    @Operation(summary = "Journal de l'établissement",
            description = "Encaissements au guichet, échéanciers redéfinis, exports comptables, "
                    + "campagnes de relance, accès ouverts et fermés. Le plus récent en tête.")
    public ResponseEntity<List<AuditEventDto>> findAll(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID actorId) {
        return ResponseEntity.ok(this.auditService.findAll(from, to, actorId));
    }
}
