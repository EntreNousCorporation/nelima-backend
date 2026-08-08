package com.ypyit.neoelima.domain.prospect.controller;

import com.ypyit.neoelima.domain.prospect.dto.DemoRequestDto;
import com.ypyit.neoelima.domain.prospect.entity.DemoRequestStatus;
import com.ypyit.neoelima.domain.prospect.service.DemoRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Suivi des demandes de démonstration, côté YPYit.
 *
 * <p>Fermé à toute autre partie : une demande porte le nom d'un directeur, son téléphone et
 * l'effectif qu'il annonce. Une école concurrente y lirait la liste des prospects du marché.
 */
@RestController
@RequestMapping("/prospects")
@RequiredArgsConstructor
@Tag(name = "Console YPYit")
public class ProspectController {

    private final DemoRequestService demoRequestService;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Demandes de démonstration reçues")
    public ResponseEntity<List<DemoRequestDto>> all() {
        return ResponseEntity.ok(this.demoRequestService.all());
    }

    @PutMapping(value = "/{id}/status", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Marque une demande traitée ou écartée",
            description = "Revenir en attente efface la date de traitement : une demande rouverte "
                    + "n'a pas été traitée.")
    public ResponseEntity<DemoRequestDto> setStatus(@PathVariable UUID id,
                                                    @RequestBody @Valid StatusForm form) {
        return ResponseEntity.ok(
                this.demoRequestService.setStatus(id, form.getStatus(), form.getNote()));
    }

    @Getter
    @Setter
    public static class StatusForm {
        @NotNull
        private DemoRequestStatus status;
        @Size(max = 500)
        private String note;
    }
}
