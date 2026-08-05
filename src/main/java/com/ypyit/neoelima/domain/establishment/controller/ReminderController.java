package com.ypyit.neoelima.domain.establishment.controller;

import com.ypyit.neoelima.domain.establishment.dto.ReminderCampaignDto;
import com.ypyit.neoelima.domain.establishment.dto.ReminderTargetDto;
import com.ypyit.neoelima.domain.establishment.form.ReminderCampaignForm;
import com.ypyit.neoelima.domain.establishment.service.ReminderCampaignService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Relances des familles en retard.
 *
 * <p>Le décompte se lit sans permission d'écriture : savoir combien de familles sont en retard
 * relève de la consultation. Envoyer, en revanche, demande {@code reminder:write} — c'est la seule
 * action du portail qui engage une dépense.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/reminders")
@Tag(name = "Relances", description = "Campagnes de relance des familles en retard")
public class ReminderController {

    private final ReminderCampaignService campaignService;

    @GetMapping(value = "/targets", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('accounting:read')")
    @Operation(summary = "Ce que chaque cible représente aujourd'hui",
            description = "Familles jointes et montant en jeu, à consulter avant d'envoyer : un "
                    + "SMS se facture à l'envoi.")
    public ResponseEntity<List<ReminderTargetDto>> targets() {
        return ResponseEntity.ok(this.campaignService.targets());
    }

    @GetMapping(value = "/campaigns", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('accounting:read')")
    @Operation(summary = "Historique des campagnes, avec les règlements qu'elles ont déclenchés")
    public ResponseEntity<List<ReminderCampaignDto>> campaigns() {
        return ResponseEntity.ok(this.campaignService.findAll());
    }

    @PostMapping(value = "/campaigns", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('reminder:write')")
    @Operation(summary = "Lance une campagne",
            description = "Les destinataires déjà relancés le jour même sont écartés, quelle que "
                    + "soit l'origine du précédent envoi. La réponse dit combien.")
    public ResponseEntity<ReminderCampaignDto> send(@RequestBody @Valid ReminderCampaignForm form) {
        return ResponseEntity.ok(this.campaignService.send(form));
    }
}
