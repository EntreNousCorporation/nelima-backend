package com.ypyit.neoelima.domain.establishment.controller;

import com.ypyit.neoelima.domain.establishment.dto.CityDto;
import com.ypyit.neoelima.domain.establishment.service.CityService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Référentiel des villes.
 *
 * <p>Ouvert à tout compte authentifié, sans permission : c'est la liste des villes de Côte d'Ivoire,
 * elle ne dit rien de personne. La réserver à YPYit obligerait à la rouvrir au premier écran
 * d'école qui aura besoin de situer un site.
 */
@RestController
@RequestMapping("/cities")
@RequiredArgsConstructor
public class CityController {

    private final CityService cityService;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Villes proposées au choix",
            description = "Par ordre alphabétique, avec leur région. Les villes désactivées — les "
                    + "saisies libres reprises à la mise en place du référentiel — n'y figurent pas.")
    public ResponseEntity<List<CityDto>> cities() {
        return ResponseEntity.ok(this.cityService.selectable());
    }
}
