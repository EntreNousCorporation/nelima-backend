package com.ypyit.neoelima.domain.establishment.controller;

import com.ypyit.neoelima.domain.establishment.dto.RoleSettingsDto;
import com.ypyit.neoelima.domain.establishment.service.RoleSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/role-settings")
@Tag(name = "Habilitations", description = "Ce que chaque rôle d'équipe voit et fait dans l'école")
public class RoleSettingsController {

    private final RoleSettingsService roleSettingsService;

    @Getter
    @Setter
    public static class RoleForm {
        private List<String> permissions;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('user_access:write')")
    @Operation(summary = "Rôles de l'école et leurs permissions, avec le catalogue")
    public ResponseEntity<RoleSettingsDto> settings() {
        return ResponseEntity.ok(this.roleSettingsService.settings());
    }

    @PutMapping(value = "/{roleCode}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('user_access:write')")
    @Operation(summary = "Règle les permissions d'un rôle d'équipe", description = "Les permissions supposées (consulter pour modifier) sont ajoutées d'office.")
    public ResponseEntity<RoleSettingsDto> update(@PathVariable String roleCode, @RequestBody RoleForm form) {
        return ResponseEntity.ok(this.roleSettingsService.update(roleCode, form.getPermissions()));
    }

    @DeleteMapping("/{roleCode}")
    @PreAuthorize("hasAuthority('user_access:write')")
    @Operation(summary = "Rend au rôle les permissions par défaut de la plateforme")
    public ResponseEntity<RoleSettingsDto> reset(@PathVariable String roleCode) {
        return ResponseEntity.ok(this.roleSettingsService.reset(roleCode));
    }
}
