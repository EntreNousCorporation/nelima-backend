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
    @PreAuthorize("hasAuthority('settings:read')")
    @Operation(summary = "Années scolaires de l'établissement",
            description = "La plus récente en tête. Les années passées sont conservées : les reçus "
                    + "et les échéances y sont datés.")
    public ResponseEntity<List<AcademicYearDto>> findAll() {
        return ResponseEntity.ok(this.academicYearService.findAll());
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('settings:write')")
    @Operation(summary = "Déclare une année scolaire",
            description = "L'activer désactive la précédente : une seule année est en cours.")
    public ResponseEntity<AcademicYearDto> create(@RequestBody @Valid AcademicYearForm form) {
        return ResponseEntity.ok(this.academicYearService.create(form));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('settings:write')")
    @Operation(summary = "Modifie une année et remplace ses périodes")
    public ResponseEntity<AcademicYearDto> update(@PathVariable UUID id,
                                                   @RequestBody @Valid AcademicYearForm form) {
        return ResponseEntity.ok(this.academicYearService.update(id, form));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('settings:write')")
    @Operation(summary = "Supprime une année",
            description = "Refusé pour l'année en cours : l'école resterait sans repère.")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        this.academicYearService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
