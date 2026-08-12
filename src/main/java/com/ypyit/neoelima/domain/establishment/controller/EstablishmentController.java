package com.ypyit.neoelima.domain.establishment.controller;

import com.ypyit.neoelima.domain.establishment.dto.EstablishmentContactCardDto;
import com.ypyit.neoelima.domain.establishment.dto.EstablishmentDto;
import com.ypyit.neoelima.domain.establishment.dto.EstablishmentLiteDto;
import com.ypyit.neoelima.domain.establishment.dto.StudentLiteDto;
import com.ypyit.neoelima.domain.establishment.form.EstablishmentCreationForm;
import com.ypyit.neoelima.domain.establishment.form.EstablishmentLevelOfStudyCreationForm;
import com.ypyit.neoelima.domain.establishment.form.EstablishmentSearchForm;
import com.ypyit.neoelima.domain.establishment.form.EstablishmentUpdateForm;
import com.ypyit.neoelima.domain.establishment.service.EstablishmentService;
import com.ypyit.neoelima.domain.establishment.service.ParentSchoolContactService;
import com.ypyit.neoelima.domain.establishment.service.StudentService;
import com.ypyit.neoelima.domain.user.dto.UserDto;
import com.ypyit.neoelima.domain.user.form.EstablishmentUserSignupForm;
import com.ypyit.neoelima.domain.user.form.UserSignupForm;
import com.ypyit.neoelima.domain.user.service.UserService;
import com.ypyit.neoelima.domain.utils.ControllerUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import com.ypyit.neoelima.config.security.CurrentUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/establishments")
@RequiredArgsConstructor
public class EstablishmentController {

    private final EstablishmentService establishmentService;

    private final CurrentUserProvider currentUserProvider;

    private final UserService userService;

    private final StudentService studentService;

    private final ParentSchoolContactService parentSchoolContactService;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UserDto> createRootUser(@RequestBody @Valid UserSignupForm creationForm) {
        var response = this.userService.createPartnerRootUser(creationForm);
        URI uri = ControllerUtils.buildMvcPathComponent(response.getId(), EstablishmentController.class);
        return ResponseEntity.created(uri).body(response);
    }

    @PostMapping(value = "/{id}/users", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('user_access:write')")
    @Operation(summary = "Crée un compte dans cet établissement",
            description = "Réservé à l'établissement lui-même et à l'équipe YPYit : un compte créé "
                    + "ailleurs donnerait accès à toutes les données de l'école visée.")
    public ResponseEntity<UserDto> createUsers(@RequestBody @Valid EstablishmentUserSignupForm creationForm, @PathVariable UUID id) {
        this.currentUserProvider.assertCanAdministerEstablishment(id);
        var response = this.userService.createPartnerUser(id, creationForm);
        URI uri = ControllerUtils.buildMvcPathComponent(response.getId(), EstablishmentController.class);
        return ResponseEntity.created(uri).body(response);
    }

    /**
     * Renvoie le courriel de bienvenue et son lien de définition de mot de passe.
     *
     * <p>Ouvert à l'équipe YPYit <em>et</em> à l'école pour son propre personnel : c'est le seul
     * recours quand le premier courriel s'est perdu ou que le jeton a expiré. La permission n'est
     * pas posée par annotation — le rôle YPYit ne porte aucune permission d'établissement, comme
     * pour la modification de fiche juste au-dessus.
     */
    @PostMapping(value = "/{id}/users/{userId}/resend-activation", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Renvoie le lien de définition de mot de passe",
            description = "Un nouveau jeton est émis et l'ancien cesse de valoir : le lien reçu en "
                    + "dernier est le seul qui fonctionne.")
    public ResponseEntity<UserDto> resendActivation(@PathVariable UUID id, @PathVariable UUID userId) {
        this.currentUserProvider.assertCanAdministerEstablishment(id);
        return ResponseEntity.ok(this.userService.resendActivationLink(id, userId));
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Page<EstablishmentLiteDto>> findAll(@PageableDefault(100) @ParameterObject Pageable page
            , @ModelAttribute @ParameterObject EstablishmentSearchForm searchForm) {
        return ResponseEntity.ok(this.establishmentService.findAll(searchForm, page));
    }

    /**
     * De quoi joindre l'école de son enfant.
     *
     * <p><strong>Aucune permission ici.</strong> Le rôle parent n'en porte aucune, et la portée est
     * dérivée de ses enfants rattachés par le service — pas du paramètre reçu.
     * {@code ParentRouteOpennessTest} le verrouille.
     */
    @GetMapping(value = "/{id}/contact", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Coordonnées de l'école d'un de ses enfants",
            description = "Téléphones, courriels — chacun avec son drapeau WhatsApp —, site et "
                    + "adresse. Servi à une famille pour l'école de ses enfants seulement : la "
                    + "fiche complète reste réservée à qui administre l'établissement.")
    public ResponseEntity<EstablishmentContactCardDto> contactCard(@PathVariable UUID id) {
        return ResponseEntity.ok(this.parentSchoolContactService.contactCardOf(id));
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Détail d'un établissement",
            description = "Son propre établissement, ou n'importe lequel pour l'équipe YPYit. La "
                    + "liste, elle, reste ouverte : l'application parent s'en sert pour rattacher "
                    + "un enfant à son école.")
    public ResponseEntity<EstablishmentDto> findById(@PathVariable UUID id) {
        this.currentUserProvider.assertCanAdministerEstablishment(id);
        if (!this.establishmentService.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(this.establishmentService.findById(id));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Modifie l'identité de l'établissement",
            description = "Son propre établissement seulement. La permission est vérifiée ici et "
                    + "non par annotation : l'équipe YPYit doit pouvoir corriger une fiche, et son "
                    + "rôle ne porte aucune permission d'établissement.")
    public ResponseEntity<EstablishmentDto> update(@RequestBody @Valid EstablishmentUpdateForm updateForm, @PathVariable("id") final UUID id) {
        this.currentUserProvider.assertCanAdministerEstablishment(id);
        this.currentUserProvider.assertPermission("settings:write");
        return ResponseEntity.ok(this.establishmentService.update(id, updateForm));
    }

    @PostMapping(value = "/subsidiaries", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<EstablishmentDto> createInternal(@RequestBody @Valid EstablishmentCreationForm creationForm) {
        var response = this.establishmentService.createInternal(creationForm);
        URI uri = ControllerUtils.buildMvcPathComponent(response.getId(), EstablishmentController.class);
        return ResponseEntity.created(uri).body(response);
    }

    @PostMapping(value = "/{id}/level-of-studies", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('fee:write')")
    public ResponseEntity<EstablishmentLiteDto> createLevelOfStudies(@RequestBody @Valid EstablishmentLevelOfStudyCreationForm creationForm, @PathVariable UUID id) {
        this.currentUserProvider.assertCanAdministerEstablishment(id);
        var response = this.establishmentService.createLevelOfStudies(id, creationForm);
        URI uri = ControllerUtils.buildMvcPathComponent(response.getId(), EstablishmentController.class);
        return ResponseEntity.created(uri).body(response);
    }

    @GetMapping(value = "/{id}/students", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('student:read')")
    @Operation(summary = "Élèves de l'établissement",
            description = "Réservé à l'établissement lui-même : ce sont des données personnelles "
                    + "de mineurs.")
    public ResponseEntity<Page<StudentLiteDto>> findByEstablishmentId(@PathVariable UUID id, @PageableDefault(100) @ParameterObject Pageable page
            , @ModelAttribute @ParameterObject EstablishmentSearchForm searchForm) {
        this.currentUserProvider.assertCanAdministerEstablishment(id);
        return ResponseEntity.ok(this.studentService.findByEstablishmentId(id, page));
    }
}
