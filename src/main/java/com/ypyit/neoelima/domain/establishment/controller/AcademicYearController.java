package com.ypyit.neoelima.domain.establishment.controller;

import com.ypyit.neoelima.domain.establishment.dto.AcademicYearDto;
import com.ypyit.neoelima.domain.establishment.form.AcademicYearForm;
import com.ypyit.neoelima.domain.establishment.service.AcademicYearService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

import java.util.List;
import java.util.UUID;

/**
 * Année scolaire et périodes.
 *
 * <p>Le réglage a un consommateur : les bornes de l'année active paraissent au calendrier de
 * l'école et à celui des familles.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/academic-years")
@Tag(name = "Année scolaire", description = "Bornes de l'année en cours et périodes")
public class AcademicYearController {

    private final AcademicYearService academicYearService;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Années scolaires de la plateforme",
            description = "La plus récente en tête. Pour un compte d'école, chaque année porte les "
                    + "trimestres et l'état de l'école ; l'année consultée par l'utilisateur est signalée.")
    public ResponseEntity<List<AcademicYearDto>> findAll() {
        return ResponseEntity.ok(this.academicYearService.findAll());
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Déclare une année scolaire (plateforme)",
            description = "Réservé à l'équipe de la plateforme. La désigner en cours retire ce statut à la précédente.")
    public ResponseEntity<AcademicYearDto> create(@RequestBody @Valid AcademicYearForm form) {
        return ResponseEntity.ok(this.academicYearService.create(form));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Modifie une année (plateforme)",
            description = "Réservé à l'équipe de la plateforme. Refusé si les trimestres d'une école sortiraient des nouvelles bornes.")
    public ResponseEntity<AcademicYearDto> update(@PathVariable UUID id,
                                                   @RequestBody @Valid AcademicYearForm form) {
        return ResponseEntity.ok(this.academicYearService.update(id, form));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Supprime une année (plateforme)",
            description = "Refusé pour l'année en cours, et dès qu'une école y travaille.")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        this.academicYearService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping(value = "/{id}/periods", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('settings:write')")
    @Operation(summary = "Trimestres de l'école pour une année",
            description = "Remplace les périodes de l'école ; chacune doit tenir dans les bornes de l'année.")
    public ResponseEntity<AcademicYearDto> definePeriods(@PathVariable UUID id,
                                                         @RequestBody @Valid List<AcademicYearForm.AcademicPeriodForm> periods) {
        return ResponseEntity.ok(this.academicYearService.definePeriods(id, periods));
    }

    @PutMapping("/consulted")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Année consultée par l'utilisateur",
            description = "Tous ses écrans passent sur cette année. Sans `academicYearId`, retour à l'année en cours.")
    public ResponseEntity<Void> consult(@RequestParam(required = false) UUID academicYearId) {
        this.academicYearService.consult(academicYearId);
        return ResponseEntity.noContent().build();
    }
}
