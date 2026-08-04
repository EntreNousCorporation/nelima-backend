package com.ypyit.neoelima.domain.establishment.controller;

import com.ypyit.neoelima.domain.establishment.dto.SchoolClassDto;
import com.ypyit.neoelima.domain.establishment.form.SchoolClassForm;
import com.ypyit.neoelima.domain.establishment.form.SchoolClassStudentsForm;
import com.ypyit.neoelima.domain.establishment.service.SchoolClassService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

@RestController
@RequiredArgsConstructor
@RequestMapping("/classes")
@Tag(name = "Classes", description = "Classes de l'établissement et affectation des élèves")
public class SchoolClassController {

    private final SchoolClassService schoolClassService;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Classes de l'établissement, avec effectif et recouvrement")
    public ResponseEntity<List<SchoolClassDto>> findAll() {
        return ResponseEntity.ok(this.schoolClassService.findAll());
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SchoolClassDto> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(this.schoolClassService.findById(id));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SchoolClassDto> create(@RequestBody @Valid SchoolClassForm form) {
        return ResponseEntity.ok(this.schoolClassService.create(form));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SchoolClassDto> update(@PathVariable UUID id,
                                                 @RequestBody @Valid SchoolClassForm form) {
        return ResponseEntity.ok(this.schoolClassService.update(id, form));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        this.schoolClassService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/{id}/students", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Affecte des élèves à la classe",
            description = "Les élèves déjà affectés ailleurs changent de classe ; ceux qui y sont "
                    + "déjà sont ignorés sans erreur.")
    public ResponseEntity<Void> assign(@PathVariable UUID id,
                                       @RequestBody @Valid SchoolClassStudentsForm form) {
        this.schoolClassService.assign(id, form.getStudentIds());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/students/{studentId}")
    public ResponseEntity<Void> unassign(@PathVariable UUID id, @PathVariable UUID studentId) {
        this.schoolClassService.unassign(id, studentId);
        return ResponseEntity.noContent().build();
    }
}
