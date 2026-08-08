package com.ypyit.neoelima.domain.subscription.controller;

import com.ypyit.neoelima.domain.subscription.dto.SubscriptionDueDto;
import com.ypyit.neoelima.domain.subscription.dto.SubscriptionInvoiceDto;
import com.ypyit.neoelima.domain.subscription.dto.SubscriptionPlanDto;
import com.ypyit.neoelima.domain.subscription.form.SubscriptionPlanForm;
import com.ypyit.neoelima.domain.subscription.service.SubscriptionPlanService;
import com.ypyit.neoelima.domain.subscription.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Abonnement des écoles à Nelima — console YPYit.
 *
 * <p>Aucune de ces routes n'est ouverte à une école : elle y lirait les tarifs consentis à ses
 * concurrentes. Le cloisonnement tient à {@code PLATFORM_CONSOLE_RESOURCES}, dans la configuration
 * de sécurité, et non à une annotation — le rôle ADMIN de YPYit ne porte aucune permission, une
 * annotation le refuserait.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/subscriptions")
@Tag(name = "Abonnements", description = "Formules, tarifs et factures d'abonnement à Nelima")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final SubscriptionPlanService planService;

    /* ---------------- Formules et tarifs ---------------- */

    @GetMapping(value = "/plans", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Grille des formules",
            description = "Palier d'effectif et tarif annuel. Tout vit en base : créer une formule "
                    + "ou renégocier un prix ne demande pas de livraison.")
    public ResponseEntity<List<SubscriptionPlanDto>> plans(
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return ResponseEntity.ok(this.planService.list(includeInactive));
    }

    @PostMapping(value = "/plans", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Crée une formule",
            description = "Son code est dérivé du libellé puis figé : établissements et factures "
                    + "le recopient, et le changer leur ferait désigner une formule disparue.")
    public ResponseEntity<SubscriptionPlanDto> createPlan(@RequestBody @Valid SubscriptionPlanForm form) {
        return ResponseEntity.ok(this.planService.create(form));
    }

    @PutMapping(value = "/plans/{id}", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Modifie une formule",
            description = "Libellé, palier, tarif et activation. Sans effet sur les factures déjà "
                    + "émises : elles ont figé leur formule et leur montant.")
    public ResponseEntity<SubscriptionPlanDto> updatePlan(@PathVariable UUID id,
                                                          @RequestBody @Valid SubscriptionPlanForm form) {
        return ResponseEntity.ok(this.planService.update(id, form));
    }

    @DeleteMapping(value = "/plans/{id}")
    @Operation(summary = "Supprime une formule inutilisée",
            description = "Refusé dès qu'une école ou une facture s'y réfère : on la désactive.")
    public ResponseEntity<Void> deletePlan(@PathVariable UUID id) {
        this.planService.delete(id);
        return ResponseEntity.noContent().build();
    }


    @PutMapping(value = "/establishments/{establishmentId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Fixe la formule d'une école",
            description = "La date de souscription ancre les périodes de facturation ; elle ne "
                    + "change plus dès qu'une facture s'appuie dessus.")
    public ResponseEntity<Void> subscribe(@PathVariable UUID establishmentId,
                                          @RequestBody @Valid SubscribeForm form) {
        this.subscriptionService.subscribe(establishmentId, form.getPlan(), form.getSubscribedAt());
        return ResponseEntity.noContent().build();
    }

    /* ---------------- Factures ---------------- */

    @GetMapping(value = "/invoices", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Toutes les factures d'abonnement, la plus récente en tête")
    public ResponseEntity<List<SubscriptionInvoiceDto>> invoices() {
        return ResponseEntity.ok(this.subscriptionService.invoices());
    }

    @GetMapping(value = "/invoices/establishment/{establishmentId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Factures d'une école")
    public ResponseEntity<List<SubscriptionInvoiceDto>> invoicesOf(@PathVariable UUID establishmentId) {
        return ResponseEntity.ok(this.subscriptionService.invoicesOf(establishmentId));
    }

    @GetMapping(value = "/due", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Écoles à facturer",
            description = "Périodes échues sans facture. Rien n'est émis automatiquement : une "
                    + "facture produite sans qu'on la regarde est une dette que personne n'a vérifiée.")
    public ResponseEntity<List<SubscriptionDueDto>> due() {
        return ResponseEntity.ok(this.subscriptionService.due());
    }

    @PostMapping(value = "/invoices/establishment/{establishmentId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Émet la facture de la période en cours")
    public ResponseEntity<SubscriptionInvoiceDto> issue(@PathVariable UUID establishmentId) {
        return ResponseEntity.ok(this.subscriptionService.issue(establishmentId));
    }

    @PostMapping(value = "/invoices/{invoiceId}/payment", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Constate un règlement reçu hors plateforme")
    public ResponseEntity<SubscriptionInvoiceDto> recordPayment(@PathVariable UUID invoiceId,
                                                                @RequestBody @Valid PaymentForm form) {
        return ResponseEntity.ok(this.subscriptionService.recordPayment(
                invoiceId, form.getPaidOn(), form.getMethod(), form.getReference()));
    }

    @PostMapping(value = "/invoices/{invoiceId}/cancellation", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Annule une facture émise par erreur",
            description = "Annulée, jamais supprimée : la suite comptable doit rester continue.")
    public ResponseEntity<SubscriptionInvoiceDto> cancel(@PathVariable UUID invoiceId,
                                                         @RequestBody @Valid CancellationForm form) {
        return ResponseEntity.ok(this.subscriptionService.cancel(invoiceId, form.getReason()));
    }

    /* ---------------- Charges utiles ---------------- */



    @Getter
    @Setter
    public static class SubscribeForm {
        @NotNull
        private String plan;
        /** Nulle au premier passage : la date du jour fait foi. */
        private LocalDate subscribedAt;
    }

    @Getter
    @Setter
    public static class PaymentForm {
        private LocalDate paidOn;
        private String method;
        private String reference;
    }

    @Getter
    @Setter
    public static class CancellationForm {
        private String reason;
    }
}
