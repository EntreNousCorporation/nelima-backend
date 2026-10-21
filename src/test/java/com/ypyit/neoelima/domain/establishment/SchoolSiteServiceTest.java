package com.ypyit.neoelima.domain.establishment;

import com.ypyit.neoelima.AbstractIntegrationTest;
import com.ypyit.neoelima.config.security.CurrentUserProvider;
import com.ypyit.neoelima.domain.establishment.dto.SchoolSiteDtos;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.repository.EstablishmentRepository;
import com.ypyit.neoelima.domain.establishment.service.SchoolSiteService;
import com.ypyit.neoelima.domain.user.entity.AdminUserEntity;
import com.ypyit.neoelima.domain.user.entity.ContactEntity;
import com.ypyit.neoelima.domain.user.entity.EstablishmentUserEntity;
import com.ypyit.neoelima.domain.user.entity.UserEntity;
import com.ypyit.neoelima.domain.user.enums.ContactType;
import com.ypyit.neoelima.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Groupes à plusieurs sites : la direction du groupe passe d'un site à l'autre, les équipes de site
 * restent sur le leur, et rien ne franchit la frontière d'un groupe.
 */
@Transactional
class SchoolSiteServiceTest extends AbstractIntegrationTest {

    @Autowired
    private SchoolSiteService siteService;
    @Autowired
    private CurrentUserProvider currentUserProvider;
    @Autowired
    private EstablishmentRepository establishmentRepository;
    @Autowired
    private UserRepository userRepository;

    private EstablishmentEntity marcory;
    private EstablishmentEntity cocody;
    private EstablishmentEntity elsewhere;
    private UUID groupId;
    private String director;
    private UUID directorId;
    private String secretary;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        this.marcory = this.school("Dubass Marcory");
        this.cocody = this.school("Dubass Cocody");
        this.elsewhere = this.school("Autre école");
        this.director = this.user(EstablishmentUserEntity.builder().establishment(this.marcory)
                .role(this.directorRole()));
        this.directorId = this.userRepository.findAll().stream()
                .filter(u -> u.getContacts().stream().anyMatch(c -> c.getValue().equals(this.director)))
                .findFirst().orElseThrow().getId();
        this.secretary = this.user(EstablishmentUserEntity.builder().establishment(this.marcory));

        this.login(this.user(AdminUserEntity.builder()));
        SchoolSiteDtos.Group group = this.siteService.saveGroup(null, "Groupe Dubass " + UUID.randomUUID(),
                List.of(this.marcory.getId(), this.cocody.getId()));
        this.groupId = group.getId();
        assertThat(group.getSites()).extracting(SchoolSiteDtos.Site::getName).containsExactly("Dubass Cocody", "Dubass Marcory");
        this.siteService.setGroupWide(this.directorId, true);
    }

    @Test
    @DisplayName("la direction du groupe affiche un autre site : tout l'espace école bascule sur lui")
    void directorSwitchesSite() {
        this.login(this.director);
        assertThat(this.siteService.sites().getSites()).hasSize(2);

        this.siteService.switchTo(this.cocody.getId());
        assertThat(this.currentUserProvider.resolveEstablishmentScope(null)).isEqualTo(this.cocody.getId());
        assertThat(this.currentUserProvider.ownEstablishmentId()).contains(this.marcory.getId());

        this.siteService.switchTo(null);
        assertThat(this.currentUserProvider.resolveEstablishmentScope(null)).isEqualTo(this.marcory.getId());
    }

    @Test
    @DisplayName("ni hors du groupe, ni pour une équipe de site")
    void boundaries() {
        this.login(this.director);
        assertThatThrownBy(() -> this.siteService.switchTo(this.elsewhere.getId())).isInstanceOf(AccessDeniedException.class);

        this.login(this.secretary);
        assertThat(this.siteService.sites().getSites()).singleElement()
                .satisfies(site -> assertThat(site.getId()).isEqualTo(this.marcory.getId()));
        assertThatThrownBy(() -> this.siteService.switchTo(this.cocody.getId())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> this.siteService.overview()).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("un site retiré du groupe ramène la direction sur son propre site")
    void detachedSiteFallsBack() {
        this.login(this.director);
        this.siteService.switchTo(this.cocody.getId());

        this.login(this.user(AdminUserEntity.builder()));
        this.siteService.saveGroup(this.groupId, "Groupe Dubass bis " + UUID.randomUUID(), List.of(this.marcory.getId()));

        this.login(this.director);
        assertThat(this.currentUserProvider.resolveEstablishmentScope(null)).isEqualTo(this.marcory.getId());
    }

    @Test
    @DisplayName("« Tous les sites » donne une ligne par site du groupe")
    void overviewListsEverySite() {
        this.login(this.director);
        assertThat(this.siteService.overview().getSites())
                .extracting(row -> row.getSite().getName())
                .containsExactly("Dubass Cocody", "Dubass Marcory");
    }

    @Test
    @DisplayName("seule la plateforme gère les groupes")
    void onlyThePlatformManagesGroups() {
        this.login(this.director);
        assertThatThrownBy(() -> this.siteService.groups()).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> this.siteService.setGroupWide(this.directorId, false)).isInstanceOf(AccessDeniedException.class);
    }

    private EstablishmentEntity school(String name) {
        return this.establishmentRepository.saveAndFlush(EstablishmentEntity.builder()
                .name(name).active(true).isPrimary(true).build());
    }

    private String user(UserEntity.UserEntityBuilder<?, ?> builder) {
        String email = "u-" + UUID.randomUUID() + "@ecole.ci";
        this.userRepository.saveAndFlush(builder.firstName("Awa").lastName("Traoré")
                .contacts(new HashSet<>(Set.of(ContactEntity.builder()
                        .type(ContactType.EMAIL).value(email).isPrimary(true).build())))
                .build());
        return email;
    }

    private void login(String email) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(email, "n/a", List.of()));
    }

    /** La direction de groupe se donne à un directeur, et « Tous les sites » demande la comptabilité. */
    @Autowired
    private com.ypyit.neoelima.domain.user.repository.RoleRepository roleRepository;

    private com.ypyit.neoelima.domain.user.entity.RoleEntity directorRole() {
        com.ypyit.neoelima.domain.user.entity.RoleEntity granted = roleWith("accounting:read");
        com.ypyit.neoelima.domain.user.entity.RoleEntity director = this.roleRepository.findByCode("DIRECTEUR")
                .orElseGet(() -> this.roleRepository.saveAndFlush(com.ypyit.neoelima.domain.user.entity.RoleEntity.builder()
                        .code("DIRECTEUR")
                        .name(com.ypyit.neoelima.domain.user.entity.TranslateEntity.builder().fr("Directeur").en("Director").build())
                        .build()));
        director.getPermissions().addAll(granted.getPermissions());
        return this.roleRepository.saveAndFlush(director);
    }
}
