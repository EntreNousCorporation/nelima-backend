package com.ypyit.neoelima.domain.prospect.controller;

import com.ypyit.neoelima.domain.prospect.form.DemoRequestForm;
import com.ypyit.neoelima.domain.prospect.service.DemoRequestService;
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
 * Dépôt d'une demande de démonstration depuis le site public.
 *
 * <p>Le seul point d'entrée non authentifié de l'application en dehors des rappels de l'agrégateur
 * et de l'authentification elle-même. Il est sous {@code /public/} pour que cela se voie dans
 * l'URL : une route ouverte qui ressemble aux autres finit par être déplacée sans qu'on y pense.
 *
 * <p>Il ne rend rien — ni identifiant, ni compteur, ni message distinguant l'accepté du rejeté.
 * Une route publique bavarde devient un moyen de sonder la base.
 */
@RestController
@RequestMapping("/public/demo-requests")
@RequiredArgsConstructor
@Tag(name = "Site public")
public class DemoRequestController {

    private final DemoRequestService demoRequestService;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Dépose une demande de démonstration",
            description = "Route ouverte, appelée par le site vitrine. Rend toujours 204, que la "
                    + "demande ait été retenue ou écartée : distinguer les cas renseignerait un automate.")
    public ResponseEntity<Void> submit(@RequestBody @Valid DemoRequestForm form) {
        this.demoRequestService.submit(form);
        return ResponseEntity.noContent().build();
    }
}
