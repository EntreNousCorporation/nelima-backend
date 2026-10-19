package com.ypyit.neoelima.domain.establishment.controller;

import java.util.Map;
import com.ypyit.neoelima.domain.establishment.service.YearPreparationService;
import com.ypyit.neoelima.domain.establishment.form.ReenrollmentForm;
import com.ypyit.neoelima.domain.establishment.dto.YearPreparationDto;
import com.ypyit.neoelima.domain.establishment.dto.AcademicYearDto;
import com.ypyit.neoelima.domain.establishment.dto.SchoolYearClosureDto;
import com.ypyit.neoelima.domain.establishment.service.SchoolYearClosureService;
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
    private final SchoolYearClosureService schoolYearClosureService;
    private final YearPreparationService yearPreparationService;
    private final com.ypyit.neoelima.domain.establishment.service.DebtCarryOverService debtCarryOverService;

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

    @GetMapping(value = "/{id}/closure", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('settings:read')")
    @Operation(summary = "Où en est la clôture de l'année dans l'école",
            description = "Reste dû par élève, paiements en ligne en cours, et si la clôture est possible.")
    public ResponseEntity<SchoolYearClosureDto> closure(@PathVariable UUID id) {
        return ResponseEntity.ok(this.schoolYearClosureService.preview(id));
    }

    @PostMapping(value = "/{id}/carry-over", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('settings:write')")
    @Operation(summary = "Reporte les impayés de l'année sur la suivante",
            description = "Une tranche par tranche due, sous un frais « Report <année> », payable à la rentrée. L'année peut ensuite se clôturer.")
    public ResponseEntity<com.ypyit.neoelima.domain.establishment.dto.CarryOverDto> carryOver(@PathVariable UUID id) {
        return ResponseEntity.ok(this.debtCarryOverService.carryOver(id));
    }

    @PostMapping(value = "/{id}/close", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('settings:write')")
    @Operation(summary = "Clôture l'année dans l'école",
            description = "Refusé tant qu'une tranche de l'année reste à encaisser, et pour l'année en cours. "
                    + "Une fois clôturée, l'année se consulte et ne se modifie plus.")
    public ResponseEntity<SchoolYearClosureDto> close(@PathVariable UUID id) {
        return ResponseEntity.ok(this.schoolYearClosureService.close(id));
    }

    @GetMapping(value = "/{id}/schools", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Écoles et état de l'année (plateforme)")
    public ResponseEntity<List<SchoolYearClosureDto.SchoolState>> schools(@PathVariable UUID id) {
        return ResponseEntity.ok(this.schoolYearClosureService.schools(id));
    }

    @PostMapping("/{id}/schools/{establishmentId}/reopen")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Rouvre l'année d'une école (plateforme)",
            description = "Réservé à l'équipe de la plateforme ; consigné au journal de l'école.")
    public ResponseEntity<Void> reopen(@PathVariable UUID id, @PathVariable UUID establishmentId) {
        this.schoolYearClosureService.reopen(id, establishmentId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/{id}/preparation", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('class:read')")
    @Operation(summary = "Rentrée de l'année dans l'école",
            description = "Classes à ouvrir (comparées à l'année précédente) et élèves de l'an passé à réinscrire.")
    public ResponseEntity<YearPreparationDto> preparation(@PathVariable UUID id) {
        return ResponseEntity.ok(this.yearPreparationService.preparation(id));
    }

    @PostMapping(value = "/{id}/preparation/classes", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('class:write')")
    @Operation(summary = "Ouvre des classes pour l'année", description = "Le titulaire de l'an passé est repris.")
    public ResponseEntity<Void> openClasses(@PathVariable UUID id, @RequestBody List<UUID> classIds) {
        this.yearPreparationService.openClasses(id, classIds);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/preparation/classes/{classId}")
    @PreAuthorize("hasAuthority('class:write')")
    @Operation(summary = "Ne pas ouvrir une classe cette année", description = "Refusé si des élèves y sont inscrits.")
    public ResponseEntity<Void> closeClass(@PathVariable UUID id, @PathVariable UUID classId) {
        this.yearPreparationService.closeClass(id, classId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/{id}/reenrollments", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('student:write')")
    @Operation(summary = "Réinscrit des élèves pour l'année",
            description = "Chacun dans sa classe de rentrée, avec les frais de son niveau. Depuis l'année visée.")
    public ResponseEntity<Map<String, Integer>> reenroll(@PathVariable UUID id, @RequestBody @Valid ReenrollmentForm form) {
        return ResponseEntity.ok(Map.of("reenrolled", this.yearPreparationService.reenroll(id, form)));
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
