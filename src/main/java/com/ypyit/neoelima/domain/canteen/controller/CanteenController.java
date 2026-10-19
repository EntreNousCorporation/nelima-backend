package com.ypyit.neoelima.domain.canteen.controller;

import com.ypyit.neoelima.domain.canteen.dto.CanteenDtos;
import com.ypyit.neoelima.domain.canteen.form.CanteenForms;
import com.ypyit.neoelima.domain.canteen.service.CanteenMenuService;
import com.ypyit.neoelima.domain.canteen.service.CanteenService;
import com.ypyit.neoelima.domain.canteen.service.StudentDietService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/canteen")
@Tag(name = "Cantine", description = "Formules, abonnements, menus, régimes et pointage")
public class CanteenController {

    private final CanteenService canteenService;
    private final CanteenMenuService canteenMenuService;
    private final StudentDietService studentDietService;

    @GetMapping(value = "/overview", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('canteen:read')")
    @Operation(summary = "Chiffres du jour : inscrits, repas servis, régimes, impayés")
    public ResponseEntity<CanteenDtos.Overview> overview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day) {
        return ResponseEntity.ok(this.canteenService.overview(day));
    }

    @GetMapping(value = "/formulas", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('canteen:read')")
    public ResponseEntity<List<CanteenDtos.Formula>> formulas() {
        return ResponseEntity.ok(this.canteenService.formulas());
    }

    @PostMapping(value = "/formulas", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('canteen:write')")
    @Operation(summary = "Crée une formule et son tarif mensuel")
    public ResponseEntity<CanteenDtos.Formula> createFormula(@RequestBody @Valid CanteenForms.Formula form) {
        return ResponseEntity.ok(this.canteenService.createFormula(form));
    }

    @PutMapping(value = "/formulas/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('canteen:write')")
    @Operation(summary = "Modifie une formule", description = "Le nouveau tarif vaut pour les abonnements à venir.")
    public ResponseEntity<CanteenDtos.Formula> updateFormula(@PathVariable UUID id, @RequestBody @Valid CanteenForms.Formula form) {
        return ResponseEntity.ok(this.canteenService.updateFormula(id, form));
    }

    @GetMapping(value = "/subscriptions", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('canteen:read')")
    @Operation(summary = "Abonnés de l'année, et leur pointage du jour")
    public ResponseEntity<List<CanteenDtos.Subscriber>> subscribers(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day) {
        return ResponseEntity.ok(this.canteenService.subscribers(day));
    }

    @PostMapping(value = "/subscriptions", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('canteen:write')")
    @Operation(summary = "Inscrit un élève à la cantine", description = "Une tranche par mois jusqu'à la fin de l'année.")
    public ResponseEntity<CanteenDtos.Subscriber> subscribe(@RequestBody @Valid CanteenForms.Subscription form) {
        return ResponseEntity.ok(this.canteenService.subscribe(form));
    }

    @PostMapping(value = "/subscriptions/{id}/end", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('canteen:write')")
    @Operation(summary = "Met fin à un abonnement", description = "Les mois suivants, non réglés, sont annulés.")
    public ResponseEntity<Void> end(@PathVariable UUID id, @RequestBody CanteenForms.End form) {
        this.canteenService.end(id, form);
        return ResponseEntity.noContent().build();
    }

    @PutMapping(value = "/attendance", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('canteen:write')")
    @Operation(summary = "Pointe un repas", description = "Absent, ou servi pour un repas ponctuel ; sans statut, retour à la règle.")
    public ResponseEntity<Void> mark(@RequestBody @Valid CanteenForms.Attendance form) {
        this.canteenService.mark(form);
        return ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/kitchen-sheet", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('canteen:read')")
    @Operation(summary = "Fiche cuisine : régimes et allergies des élèves attendus")
    public ResponseEntity<List<CanteenDtos.KitchenLine>> kitchenSheet(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day) {
        return ResponseEntity.ok(this.canteenService.kitchenSheet(day));
    }

    @GetMapping(value = "/menu", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('canteen:read')")
    @Operation(summary = "Menu de la semaine qui contient ce jour")
    public ResponseEntity<List<CanteenDtos.MenuDay>> menu(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day) {
        return ResponseEntity.ok(this.canteenMenuService.week(day));
    }

    @PutMapping(value = "/menu", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('canteen:write')")
    public ResponseEntity<List<CanteenDtos.MenuDay>> saveMenu(@RequestBody @Valid CanteenForms.Menu form) {
        return ResponseEntity.ok(this.canteenMenuService.save(form));
    }

    @PostMapping("/menu/publish")
    @PreAuthorize("hasAuthority('canteen:write')")
    @Operation(summary = "Publie ou retire le menu de la semaine", description = "Publié, il est visible des familles.")
    public ResponseEntity<List<CanteenDtos.MenuDay>> publish(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day,
            @RequestParam(defaultValue = "true") boolean published) {
        return ResponseEntity.ok(this.canteenMenuService.publish(day, published));
    }

    @GetMapping(value = "/menu/mine", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Menu publié des écoles de mes enfants (application parent)")
    public ResponseEntity<Map<String, List<CanteenDtos.MenuDay>>> mine(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day) {
        return ResponseEntity.ok(this.canteenMenuService.mine(day));
    }

    @GetMapping(value = "/students/{studentId}/diets", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('student:read')")
    @Operation(summary = "Régimes et allergies de l'élève")
    public ResponseEntity<List<CanteenDtos.Diet>> diets(@PathVariable UUID studentId) {
        return ResponseEntity.ok(this.studentDietService.diets(studentId));
    }

    @PutMapping(value = "/students/{studentId}/diets", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('student:write')")
    public ResponseEntity<List<CanteenDtos.Diet>> replaceDiets(@PathVariable UUID studentId,
                                                               @RequestBody List<CanteenForms.Diet> diets) {
        return ResponseEntity.ok(this.studentDietService.replace(studentId, diets));
    }
}
