package com.ypyit.neoelima.domain.establishment.controller;

import com.ypyit.neoelima.domain.establishment.dto.DashboardSummaryDto;
import com.ypyit.neoelima.domain.establishment.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping(value = "/summary", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Chiffres d'accueil de l'établissement",
            description = "Effectif, encaissements du mois, reste dû et retards, plus les derniers "
                    + "reçus. Les totaux sont agrégés en base : les additionner côté client "
                    + "donnerait des montants faux dès que les données dépassent une page.")
    public ResponseEntity<DashboardSummaryDto> summary(
            @RequestParam(value = "establishmentId", required = false) UUID establishmentId) {
        return ResponseEntity.ok(this.dashboardService.summaryOf(establishmentId));
    }
}
