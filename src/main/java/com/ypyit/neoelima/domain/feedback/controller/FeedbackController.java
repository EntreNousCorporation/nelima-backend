package com.ypyit.neoelima.domain.feedback.controller;

import com.ypyit.neoelima.domain.feedback.form.FeedbackForm;
import com.ypyit.neoelima.domain.feedback.service.FeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Les suggestions déposées depuis l'application.
 *
 * <p><strong>Aucune permission ici.</strong> Le rôle parent n'en porte aucune, et il n'y a rien à
 * autoriser au-delà d'être authentifié : on dépose pour soi.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/feedback")
@Tag(name = "Suggestions", description = "Ce qu'un parent a à nous dire")
public class FeedbackController {

    private final FeedbackService feedbackService;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Dépose une suggestion",
            description = "Enregistrée et transmise à YPYit. Répond 204 même quand la limitation de "
                    + "débit s'applique : distinguer les deux cas apprendrait surtout à la "
                    + "contourner.")
    public ResponseEntity<Void> submit(@RequestBody @Valid FeedbackForm form) {
        this.feedbackService.submit(form);
        return ResponseEntity.noContent().build();
    }
}
