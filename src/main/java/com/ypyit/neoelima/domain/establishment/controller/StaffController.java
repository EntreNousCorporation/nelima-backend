package com.ypyit.neoelima.domain.establishment.controller;

import com.ypyit.neoelima.domain.establishment.dto.StaffDto;
import com.ypyit.neoelima.domain.establishment.form.StaffClassesForm;
import com.ypyit.neoelima.domain.establishment.form.StaffForm;
import com.ypyit.neoelima.domain.establishment.service.StaffService;
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

@RestController
@RequiredArgsConstructor
@RequestMapping("/staff")
@Tag(name = "Personnel", description = "Répertoire du personnel de l'établissement")
public class StaffController {

    private final StaffService staffService;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('staff:read')")
    @Operation(summary = "Personnel de l'établissement",
            description = "Le salaire et la charge horaire ne sont servis qu'aux appelants portant "
                    + "staff:read_salary ; ils sont absents de la réponse pour les autres.")
    public ResponseEntity<List<StaffDto>> findAll(
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return ResponseEntity.ok(this.staffService.findAll(includeInactive));
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('staff:read')")
    public ResponseEntity<StaffDto> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(this.staffService.findById(id));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('staff:write')")
    public ResponseEntity<StaffDto> create(@RequestBody @Valid StaffForm form) {
        return ResponseEntity.ok(this.staffService.create(form));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('staff:write')")
    public ResponseEntity<StaffDto> update(@PathVariable UUID id, @RequestBody @Valid StaffForm form) {
        return ResponseEntity.ok(this.staffService.update(id, form));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('staff:write')")
    @Operation(summary = "Retire un membre du répertoire",
            description = "Désactive la fiche dès qu'un historique existe ; ne la supprime que "
                    + "lorsque rien n'a encore été enregistré au nom de la personne.")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        this.staffService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/{id}/classes", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('staff:write')")
    @Operation(summary = "Rattache le membre à des classes",
            description = "Les classes déjà rattachées sont ignorées sans erreur.")
    public ResponseEntity<StaffDto> assignClasses(@PathVariable UUID id,
                                                  @RequestBody @Valid StaffClassesForm form) {
        return ResponseEntity.ok(this.staffService.assignClasses(id, form.getClassIds()));
    }

    @DeleteMapping(value = "/{id}/classes/{classId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('staff:write')")
    public ResponseEntity<StaffDto> unassignClass(@PathVariable UUID id, @PathVariable UUID classId) {
        return ResponseEntity.ok(this.staffService.unassignClass(id, classId));
    }
}
