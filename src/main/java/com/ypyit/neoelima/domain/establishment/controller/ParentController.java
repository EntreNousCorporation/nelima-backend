package com.ypyit.neoelima.domain.establishment.controller;

import com.ypyit.neoelima.domain.establishment.dto.ParentListItemDto;
import com.ypyit.neoelima.domain.establishment.service.ParentListService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Listes de parents. Deux routes, deux surfaces : le portail de l'école et le back-office YPYit.
 *
 * <p>Aucun préfixe de classe : les deux chemins vivent dans des espaces de nommage distincts —
 * {@code /parents} pour l'école, {@code /admin} pour l'opérateur — que la sécurité protège
 * différemment.
 */
@RestController
@RequiredArgsConstructor
public class ParentController {

    private final ParentListService parentListService;

    /**
     * Parents de l'établissement de l'utilisateur connecté.
     *
     * <p>Même autorité que la lecture des élèves : le personnel qui peut lire la liste des élèves
     * peut lire celle de leurs parents. La portée établissement est imposée côté serveur, jamais
     * reçue en paramètre — un membre d'une école ne voit que les parents de la sienne.
     */
    @GetMapping(value = "/parents/search", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Parents ayant un enfant dans l'établissement de l'utilisateur connecté",
            description = "Portée imposée par le serveur : le personnel ne voit que les parents de "
                    + "son école, et de chaque parent que les enfants inscrits chez elle.")
    @PreAuthorize("hasAuthority('student:read')")
    public ResponseEntity<Page<ParentListItemDto>> search(
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 50) @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(this.parentListService.searchForEstablishment(keyword, pageable));
    }

    /**
     * Tous les parents du parc, pour le back-office YPYit.
     *
     * <p>L'accès est réservé à l'équipe plateforme au niveau du filtre de sécurité
     * ({@code PLATFORM_CONSOLE_RESOURCES}), comme {@code /dashboard/platform} : le rôle
     * administrateur ne porte aucune permission, une annotation {@code @PreAuthorize} le refuserait
     * à tort. Une école n'a rien à voir des parents des autres écoles.
     */
    @GetMapping(value = "/admin/parents", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Tous les parents de la plateforme, réservé à l'équipe YPYit",
            description = "Chaque enfant porte le nom de son établissement. Le filtre "
                    + "establishmentId ne retient que les parents ayant un enfant dans cette école, "
                    + "sans masquer leurs autres enfants.")
    public ResponseEntity<Page<ParentListItemDto>> adminList(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UUID establishmentId,
            @PageableDefault(size = 50) @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(
                this.parentListService.searchForPlatform(keyword, establishmentId, pageable));
    }
}
