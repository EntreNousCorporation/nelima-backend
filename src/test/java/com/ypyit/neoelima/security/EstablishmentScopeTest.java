package com.ypyit.neoelima.security;

import com.ypyit.neoelima.AbstractIntegrationTest;
import com.ypyit.neoelima.config.security.CurrentUserProvider;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.form.EstablishmentUpdateForm;
import com.ypyit.neoelima.domain.establishment.repository.EstablishmentRepository;
import com.ypyit.neoelima.domain.establishment.service.EstablishmentService;
import com.ypyit.neoelima.domain.user.entity.ContactEntity;
import com.ypyit.neoelima.domain.user.entity.EstablishmentUserEntity;
import com.ypyit.neoelima.domain.user.entity.StudentParentUserEntity;
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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cloisonnement des établissements.
 *
 * <p>Le fait d'être authentifié ne dit rien de l'école à laquelle on appartient. Sans ce contrôle,
 * l'identifiant reçu dans l'URL suffisait à écrire chez une autre école : modifier son identité,
 * y créer un compte — donc en prendre le contrôle — et lire la liste de ses élèves, qui sont des
 * données personnelles de mineurs.
 */
@Transactional
class EstablishmentScopeTest extends AbstractIntegrationTest {

    @Autowired
    private CurrentUserProvider currentUserProvider;
    @Autowired
    private EstablishmentRepository establishmentRepository;
    @Autowired
    private EstablishmentService establishmentService;
    @Autowired
    private UserRepository userRepository;

    private EstablishmentEntity mine;
    private EstablishmentEntity someoneElses;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        this.mine = this.school();
        this.someoneElses = this.school();
    }

    @Test
    @DisplayName("une école agit sur la sienne")
    void ownEstablishmentIsAllowed() {
        this.authenticateOn(this.mine);

        assertThatCode(() -> this.currentUserProvider.assertCanAdministerEstablishment(this.mine.getId()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("une école n'agit pas sur celle d'une autre")
    void anotherEstablishmentIsRefused() {
        this.authenticateOn(this.mine);

        assertThatThrownBy(() -> this.currentUserProvider
                .assertCanAdministerEstablishment(this.someoneElses.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("un parent n'administre aucune école")
    void aParentAdministersNothing() {
        this.authenticateAsParent();

        assertThatThrownBy(() -> this.currentUserProvider
                .assertCanAdministerEstablishment(this.mine.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("une modification partielle ne rétrograde pas l'établissement principal")
    void aPartialUpdateKeepsThePrimaryFlag() {
        this.authenticateOn(this.mine);

        // L'écran des paramètres n'a rien à dire du drapeau « principal » et ne l'envoie pas.
        // Tant que le formulaire portait une primitive, l'omission valait « false ».
        this.establishmentService.update(this.mine.getId(), EstablishmentUpdateForm.builder()
                .name("École rebaptisée " + UUID.randomUUID())
                .shortName("ERB")
                .build());

        assertThat(this.establishmentRepository.findById(this.mine.getId()).orElseThrow().isPrimary())
                .isTrue();
    }

    @Test
    @DisplayName("le drapeau principal, envoyé explicitement, s'écrit enfin")
    void anExplicitPrimaryFlagIsPersisted() {
        this.authenticateOn(this.mine);

        // B7 : `toUpdate` n'écrivait jamais `isPrimary` — écart de nom entre le formulaire
        // (« isPrimary ») et l'entité (« primary »). Une école mal marquée restait invisible
        // (`findAll` filtre sur `isPrimary = true`) et l'API de correction répondait 200 sans effet.
        this.establishmentService.update(this.mine.getId(), EstablishmentUpdateForm.builder()
                .name(this.mine.getName()).isPrimary(false).build());
        assertThat(this.establishmentRepository.findById(this.mine.getId()).orElseThrow().isPrimary())
                .as("un faux explicite doit se poser")
                .isFalse();

        this.establishmentService.update(this.mine.getId(), EstablishmentUpdateForm.builder()
                .name(this.mine.getName()).isPrimary(true).build());
        assertThat(this.establishmentRepository.findById(this.mine.getId()).orElseThrow().isPrimary())
                .as("et la correction doit rendre l'école de nouveau visible")
                .isTrue();
    }

    private EstablishmentEntity school() {
        return this.establishmentRepository.saveAndFlush(EstablishmentEntity.builder()
                .name("École " + UUID.randomUUID()).active(true).isPrimary(true).build());
    }

    private void authenticateOn(EstablishmentEntity establishment) {
        String email = "direction-" + UUID.randomUUID() + "@ecole.ci";
        this.userRepository.saveAndFlush(EstablishmentUserEntity.builder()
                .firstName("Awa").lastName("Traoré").establishment(establishment)
                .contacts(primaryEmail(email)).build());
        authenticate(email);
    }

    private void authenticateAsParent() {
        String email = "parent-" + UUID.randomUUID() + "@gmail.com";
        this.userRepository.saveAndFlush(StudentParentUserEntity.builder()
                .firstName("Jean").lastName("Kouassi").contacts(primaryEmail(email)).build());
        authenticate(email);
    }

    private static Set<ContactEntity> primaryEmail(String email) {
        return new HashSet<>(Set.of(ContactEntity.builder()
                .type(ContactType.EMAIL).value(email).isPrimary(true).build()));
    }

    private static void authenticate(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "n/a", List.of()));
    }
}
