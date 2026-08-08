package com.ypyit.neoelima.domain.subscription.controller;

import com.ypyit.neoelima.domain.subscription.dto.SubscriptionPlanDto;
import com.ypyit.neoelima.domain.subscription.service.SubscriptionPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Grille tarifaire du site public.
 *
 * <p>Séparée de {@code /subscriptions/plans}, qui reste fermée à YPYit : cette route-là sert aussi
 * le nombre d'écoles par palier et ce que chacun a rapporté. Ouvrir la grille en lecture n'oblige
 * pas à ouvrir le chiffre d'affaires du parc.
 *
 * <p>Elle ne rend que ce qui a été explicitement publié depuis la console. Une formule créée pour
 * un réseau, un tarif de lancement réservé aux pilotes restent invisibles tant que personne ne les
 * a cochés.
 */
@RestController
@RequestMapping("/public/plans")
@RequiredArgsConstructor
@Tag(name = "Site public")
public class PublicPlanController {

    private final SubscriptionPlanService planService;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Grille tarifaire publiée",
            description = "Formules actives et marquées publiques, dans l'ordre d'affichage. "
                    + "Sans identifiants ni compteurs : le site n'en a pas besoin.")
    public ResponseEntity<List<SubscriptionPlanDto>> plans() {
        return ResponseEntity.ok(this.planService.publicGrid());
    }
}
