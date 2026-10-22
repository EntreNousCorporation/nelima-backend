package com.ypyit.neoelima.domain.transport.controller;

import com.ypyit.neoelima.domain.transport.dto.TransportDtos;
import com.ypyit.neoelima.domain.transport.form.TransportForms;
import com.ypyit.neoelima.domain.transport.service.TransportService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/transport")
@Tag(name = "Transport scolaire", description = "Lignes, arrêts, cars, chauffeurs, abonnés et suivi du jour")
public class TransportController {

    private final TransportService transportService;

    @GetMapping(value = "/overview", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('transport:read')")
    @Operation(summary = "Chiffres du jour et alertes (retards, incidents)")
    public ResponseEntity<TransportDtos.Overview> overview() {
        return ResponseEntity.ok(this.transportService.overview());
    }

    @GetMapping(value = "/lines", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('transport:read')")
    public ResponseEntity<List<TransportDtos.Line>> lines() {
        return ResponseEntity.ok(this.transportService.lines());
    }

    @PostMapping(value = "/lines", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('transport:write')")
    @Operation(summary = "Crée une ligne, ses arrêts et son tarif mensuel")
    public ResponseEntity<TransportDtos.Line> createLine(@RequestBody @Valid TransportForms.Line form) {
        return ResponseEntity.ok(this.transportService.saveLine(null, form));
    }

    @PutMapping(value = "/lines/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('transport:write')")
    @Operation(summary = "Modifie une ligne et remplace ses arrêts", description = "Un arrêt qui garde son identifiant garde ses abonnés.")
    public ResponseEntity<TransportDtos.Line> updateLine(@PathVariable UUID id, @RequestBody @Valid TransportForms.Line form) {
        return ResponseEntity.ok(this.transportService.saveLine(id, form));
    }

    @PutMapping(value = "/lines/{id}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('transport:write')")
    @Operation(summary = "Statut de la ligne aujourd'hui", description = "Un retard ou un incident prévient les familles de la ligne.")
    public ResponseEntity<TransportDtos.Line> setStatus(@PathVariable UUID id, @RequestBody @Valid TransportForms.Status form) {
        return ResponseEntity.ok(this.transportService.setStatus(id, form));
    }

    @GetMapping(value = "/vehicles", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('transport:read')")
    @Operation(summary = "Parc de véhicules de l'école et leurs affectations")
    public ResponseEntity<List<TransportDtos.Vehicle>> vehicles() {
        return ResponseEntity.ok(this.transportService.vehicles());
    }

    @PostMapping(value = "/vehicles", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('transport:write')")
    @Operation(summary = "Ajouter un véhicule au parc")
    public ResponseEntity<TransportDtos.Vehicle> createVehicle(@RequestBody @Valid TransportForms.Vehicle form) {
        return ResponseEntity.ok(this.transportService.saveVehicle(null, form));
    }

    @PutMapping(value = "/vehicles/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('transport:write')")
    @Operation(summary = "Modifier un véhicule ; les lignes qui l'affectent suivent")
    public ResponseEntity<TransportDtos.Vehicle> updateVehicle(@PathVariable UUID id, @RequestBody @Valid TransportForms.Vehicle form) {
        return ResponseEntity.ok(this.transportService.saveVehicle(id, form));
    }

    @GetMapping(value = "/drivers", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('transport:read')")
    public ResponseEntity<List<TransportDtos.Driver>> drivers() {
        return ResponseEntity.ok(this.transportService.drivers());
    }

    @PostMapping(value = "/drivers", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('transport:write')")
    public ResponseEntity<TransportDtos.Driver> createDriver(@RequestBody @Valid TransportForms.Driver form) {
        return ResponseEntity.ok(this.transportService.saveDriver(null, form));
    }

    @PutMapping(value = "/drivers/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('transport:write')")
    public ResponseEntity<TransportDtos.Driver> updateDriver(@PathVariable UUID id, @RequestBody @Valid TransportForms.Driver form) {
        return ResponseEntity.ok(this.transportService.saveDriver(id, form));
    }

    @GetMapping(value = "/subscriptions", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('transport:read')")
    @Operation(summary = "Élèves transportés, d'une ligne ou de toutes")
    public ResponseEntity<List<TransportDtos.Rider>> riders(@RequestParam(required = false) UUID lineId) {
        return ResponseEntity.ok(this.transportService.riders(lineId));
    }

    @PostMapping(value = "/subscriptions", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('transport:write')")
    @Operation(summary = "Inscrit un élève sur une ligne", description = "Une tranche par mois jusqu'à la fin de l'année ; refusé si le car est complet.")
    public ResponseEntity<TransportDtos.Rider> subscribe(@RequestBody @Valid TransportForms.Subscription form) {
        return ResponseEntity.ok(this.transportService.subscribe(form));
    }

    @PostMapping(value = "/subscriptions/{id}/end", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('transport:write')")
    public ResponseEntity<Void> end(@PathVariable UUID id, @RequestBody TransportForms.End form) {
        this.transportService.end(id, form);
        return ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/mine", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "La ligne de mes enfants et où elle en est (application parent)")
    public ResponseEntity<List<TransportDtos.ChildRide>> mine() {
        return ResponseEntity.ok(this.transportService.mine());
    }
}
