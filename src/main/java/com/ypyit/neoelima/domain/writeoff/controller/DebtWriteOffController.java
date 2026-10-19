package com.ypyit.neoelima.domain.writeoff.controller;

import com.ypyit.neoelima.domain.writeoff.dto.WriteOffDto;
import com.ypyit.neoelima.domain.writeoff.form.WriteOffForms;
import com.ypyit.neoelima.domain.writeoff.service.DebtWriteOffService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/write-offs")
@Tag(name = "Passages en perte", description = "Dettes irrécouvrables : proposées par la comptabilité, validées par la direction")
public class DebtWriteOffController {

    private final DebtWriteOffService writeOffService;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('writeoff:propose') or hasAuthority('writeoff:approve')")
    @Operation(summary = "Demandes de l'établissement, les plus récentes d'abord")
    public ResponseEntity<List<WriteOffDto>> list() {
        return ResponseEntity.ok(this.writeOffService.list());
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('writeoff:propose')")
    @Operation(summary = "Propose de passer une tranche en perte", description = "La tranche reste due jusqu'à la validation.")
    public ResponseEntity<WriteOffDto> propose(@RequestBody @Valid WriteOffForms.Proposal form) {
        return ResponseEntity.ok(this.writeOffService.propose(form));
    }

    @PostMapping(value = "/{id}/approve", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('writeoff:approve')")
    @Operation(summary = "Valide la demande : la tranche passe en perte", description = "Pas par la personne qui l'a proposée.")
    public ResponseEntity<WriteOffDto> approve(@PathVariable UUID id, @RequestBody(required = false) @Valid WriteOffForms.Decision form) {
        return ResponseEntity.ok(this.writeOffService.approve(id, form));
    }

    @PostMapping(value = "/{id}/reject", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('writeoff:approve')")
    @Operation(summary = "Refuse la demande, avec un motif : la tranche reste due")
    public ResponseEntity<WriteOffDto> reject(@PathVariable UUID id, @RequestBody @Valid WriteOffForms.Decision form) {
        return ResponseEntity.ok(this.writeOffService.reject(id, form));
    }
}
