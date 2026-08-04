package com.ypyit.neoelima.domain.payment.controller;

import com.ypyit.neoelima.domain.payment.service.BillingSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * Réglages de facturation de la plateforme.
 *
 * <p>L'écriture est réservée à l'admin YPYit par la configuration de sécurité : le taux détermine ce
 * que paient toutes les familles de toutes les écoles.
 */
@RestController
@RequestMapping("/platform-settings/billing")
@RequiredArgsConstructor
public class BillingSettingsController {

    private final BillingSettingsService billingSettingsService;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Taux de commission en vigueur",
            description = "Fraction appliquée au montant de la tranche lors d'un paiement en ligne.")
    public ResponseEntity<BillingSettingsDto> current() {
        return ResponseEntity.ok(new BillingSettingsDto(this.billingSettingsService.currentRate()));
    }

    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Modifie le taux de commission",
            description = "Prend effet au paiement suivant, sans redéploiement. Le taux s'exprime en "
                    + "fraction : 0,02 pour 2 %. Au-delà de 0,20, la saisie est refusée — c'est "
                    + "presque toujours un pourcentage écrit à la place d'une fraction.")
    public ResponseEntity<BillingSettingsDto> update(@RequestBody @Valid BillingSettingsForm form) {
        return ResponseEntity.ok(
                new BillingSettingsDto(this.billingSettingsService.updateRate(form.getCommissionRate())));
    }

    @Getter
    @AllArgsConstructor
    public static class BillingSettingsDto {
        private BigDecimal commissionRate;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class BillingSettingsForm {
        @NotNull
        private BigDecimal commissionRate;
    }
}
