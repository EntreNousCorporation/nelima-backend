package com.ypyit.neoelima.domain.establishment.controller;

import com.ypyit.neoelima.domain.establishment.dto.ActivityDto;
import com.ypyit.neoelima.domain.establishment.dto.ActivityEnrollmentDto;
import com.ypyit.neoelima.domain.establishment.enums.EnrollmentSource;
import com.ypyit.neoelima.domain.establishment.form.ActivityClassesForm;
import com.ypyit.neoelima.domain.establishment.form.ActivityEnrollmentForm;
import com.ypyit.neoelima.domain.establishment.form.ActivityForm;
import com.ypyit.neoelima.domain.establishment.service.ActivityEnrollmentService;
import com.ypyit.neoelima.domain.establishment.service.ActivityService;
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
 * Activités extra-scolaires.
 *
 * <p>Deux publics dans le même contrôleur, et la distinction est vitale : les routes du portail
 * école exigent une permission, celles de l'application parent n'en exigent <em>aucune</em>. Le rôle
 * parent n'en porte pas et n'en portera pas — son accès se vérifie enfant par enfant. Y poser un
 * {@code @PreAuthorize} répondrait 403 à toutes les familles.
 *
 * <p>{@code ParentRouteOpennessTest} recense les routes parent et échoue si l'une d'elles vient à
 * porter une annotation.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/activities")
@Tag(name = "Activités", description = "Activités extra-scolaires et inscriptions")
public class ActivityController {

    private final ActivityService activityService;
    private final ActivityEnrollmentService enrollmentService;

    /* ---------- Portail école ---------- */

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('activity:read')")
    @Operation(summary = "Catalogue des activités de l'établissement")
    public ResponseEntity<List<ActivityDto>> findAll() {
        return ResponseEntity.ok(this.activityService.findAll());
    }

    @GetMapping(value = "/enrollments", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('activity:read')")
    @Operation(summary = "Inscriptions de l'établissement, la plus récente en tête")
    public ResponseEntity<List<ActivityEnrollmentDto>> enrollments() {
        return ResponseEntity.ok(this.enrollmentService.findAll());
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('activity:read')")
    public ResponseEntity<ActivityDto> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(this.activityService.findById(id));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('activity:write')")
    public ResponseEntity<ActivityDto> create(@RequestBody @Valid ActivityForm form) {
        return ResponseEntity.ok(this.activityService.create(form));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('activity:write')")
    public ResponseEntity<ActivityDto> update(@PathVariable UUID id,
                                              @RequestBody @Valid ActivityForm form) {
        return ResponseEntity.ok(this.activityService.update(id, form));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('activity:write')")
    @Operation(summary = "Retire l'activité du catalogue",
            description = "La suspend dès qu'une inscription existe ; ne la supprime que si "
                    + "personne ne s'y est jamais inscrit.")
    public ResponseEntity<Void> suspend(@PathVariable UUID id) {
        this.activityService.suspend(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping(value = "/{id}/classes", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('activity:write')")
    @Operation(summary = "Désigne les classes conviées",
            description = "Remplace l'affectation entière : l'écran présente des cases à cocher.")
    public ResponseEntity<ActivityDto> assignClasses(@PathVariable UUID id,
                                                     @RequestBody @Valid ActivityClassesForm form) {
        return ResponseEntity.ok(this.activityService.assignClasses(id, form));
    }

    @PostMapping(value = "/{id}/enrollments", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('activity:write')")
    @Operation(summary = "Inscrit des élèves",
            description = "Au-delà de la capacité, l'inscription part en liste d'attente et ne "
                    + "produit aucune dette tant qu'une place n'est pas obtenue.")
    public ResponseEntity<List<ActivityEnrollmentDto>> enroll(@PathVariable UUID id,
                                                              @RequestBody @Valid ActivityEnrollmentForm form) {
        return ResponseEntity.ok(this.enrollmentService.enroll(id, form.getStudentIds(),
                EnrollmentSource.SCHOOL));
    }

    @DeleteMapping("/{id}/enrollments/{studentId}")
    @PreAuthorize("hasAuthority('activity:write')")
    @Operation(summary = "Annule une inscription",
            description = "Refusé dès qu'une tranche a été réglée. La place libérée revient au "
                    + "premier de la liste d'attente.")
    public ResponseEntity<Void> cancel(@PathVariable UUID id, @PathVariable UUID studentId) {
        this.enrollmentService.cancel(id, studentId);
        return ResponseEntity.noContent().build();
    }

    /* ---------- Application parent — sans exigence de permission ---------- */

    @GetMapping(value = "/open", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Activités auxquelles cet enfant peut s'inscrire",
            description = "Réservé au tuteur de l'élève et à son école : l'accès se vérifie élève "
                    + "par élève, sans permission.")
    public ResponseEntity<List<ActivityDto>> openTo(@RequestParam UUID studentId) {
        return ResponseEntity.ok(this.enrollmentService.openTo(studentId));
    }

    @PostMapping(value = "/{id}/enrollments/mine", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Inscrit son propre enfant")
    public ResponseEntity<ActivityEnrollmentDto> enrollMyChild(@PathVariable UUID id,
                                                               @RequestParam UUID studentId) {
        return ResponseEntity.ok(this.enrollmentService.enrollMyChild(id, studentId));
    }

    @DeleteMapping("/{id}/enrollments/mine/{studentId}")
    @Operation(summary = "Retire son propre enfant")
    public ResponseEntity<Void> cancelMyChild(@PathVariable UUID id, @PathVariable UUID studentId) {
        this.enrollmentService.cancelMyChild(id, studentId);
        return ResponseEntity.noContent().build();
    }
}
