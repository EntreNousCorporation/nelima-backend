package com.ypyit.neoelima.domain.establishment.controller;

import com.ypyit.neoelima.domain.establishment.dto.SchoolSiteDtos;
import com.ypyit.neoelima.domain.establishment.service.SchoolSiteService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Sites", description = "Groupes scolaires à plusieurs sites")
public class SchoolSiteController {

    private final SchoolSiteService siteService;

    @GetMapping(value = "/sites", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Le site affiché et ceux que l'appelant peut afficher")
    public ResponseEntity<SchoolSiteDtos.Sites> sites() {
        return ResponseEntity.ok(this.siteService.sites());
    }

    @PutMapping(value = "/sites/current", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Change le site affiché (direction de groupe)", description = "Sans identifiant : retour au site de rattachement.")
    public ResponseEntity<SchoolSiteDtos.Sites> switchTo(@RequestParam(required = false) UUID establishmentId) {
        return ResponseEntity.ok(this.siteService.switchTo(establishmentId));
    }

    @GetMapping(value = "/sites/overview", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "« Tous les sites » : chiffres de l'année, site par site (direction de groupe)")
    public ResponseEntity<SchoolSiteDtos.Overview> overview() {
        return ResponseEntity.ok(this.siteService.overview());
    }

    /* ---------------- Back-office de la plateforme ---------------- */

    @Getter
    @Setter
    public static class GroupForm {
        private String name;
        private List<UUID> establishmentIds;
    }

    @Getter
    @Setter
    public static class GroupWideForm {
        private boolean groupWide;
    }

    @GetMapping(value = "/admin/school-groups", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Groupes scolaires, leurs sites et leurs comptes (plateforme)")
    public ResponseEntity<List<SchoolSiteDtos.Group>> groups() {
        return ResponseEntity.ok(this.siteService.groups());
    }

    @PostMapping(value = "/admin/school-groups", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SchoolSiteDtos.Group> create(@RequestBody GroupForm form) {
        return ResponseEntity.ok(this.siteService.saveGroup(null, form.getName(), form.getEstablishmentIds()));
    }

    @PutMapping(value = "/admin/school-groups/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SchoolSiteDtos.Group> update(@PathVariable UUID id, @RequestBody GroupForm form) {
        return ResponseEntity.ok(this.siteService.saveGroup(id, form.getName(), form.getEstablishmentIds()));
    }

    @DeleteMapping("/admin/school-groups/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Dissout le groupe : ses sites redeviennent des écoles indépendantes")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        this.siteService.deleteGroup(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping(value = "/admin/users/{userId}/group-wide", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Fait d'un compte d'école une direction de groupe, ou l'en retire")
    public ResponseEntity<SchoolSiteDtos.Member> groupWide(@PathVariable UUID userId, @RequestBody GroupWideForm form) {
        return ResponseEntity.ok(this.siteService.setGroupWide(userId, form.isGroupWide()));
    }
}
