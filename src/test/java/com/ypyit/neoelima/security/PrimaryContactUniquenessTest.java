package com.ypyit.neoelima.security;

import com.ypyit.neoelima.AbstractIntegrationTest;
import com.ypyit.neoelima.common.exception.DuplicateResourceException;
import com.ypyit.neoelima.domain.user.entity.ContactEntity;
import com.ypyit.neoelima.domain.user.entity.StudentParentUserEntity;
import com.ypyit.neoelima.domain.user.entity.UserEntity;
import com.ypyit.neoelima.domain.user.enums.ContactType;
import com.ypyit.neoelima.domain.user.form.ContactUpdateForm;
import com.ypyit.neoelima.domain.user.form.UserUpdateForm;
import com.ypyit.neoelima.domain.user.repository.UserRepository;
import com.ypyit.neoelima.domain.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Un contact principal ne se partage pas entre deux comptes.
 *
 * <p>C'est un identifiant de connexion : deux comptes de même contact principal rendraient
 * {@code findByPrimaryContact} ambigu — deux lignes sur un {@code Optional} — et l'échec tomberait
 * dans le filtre JWT, sur chaque requête portant un jeton, en 500 pour toute la plateforme. La
 * création le vérifiait déjà ; la mise à jour, nulle part — c'était la porte, refermée ici.
 *
 * <p>Le contrôle est applicatif et non porté par un index : la table {@code contact} est partagée
 * avec les établissements, qui peuvent légitimement se partager un standard, et
 * {@code findByPrimaryContact} — qui ne joint que {@code user_contacts} — cible exactement les
 * comptes, eux seuls concernés par l'ambiguïté de connexion.
 */
@Transactional
class PrimaryContactUniquenessTest extends AbstractIntegrationTest {

    @Autowired
    private UserService userService;
    @Autowired
    private UserRepository userRepository;

    private UserEntity awa;
    private UserEntity bob;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        this.awa = this.aParentReachableAt("awa-" + UUID.randomUUID() + "@gmail.com");
        this.bob = this.aParentReachableAt("bob-" + UUID.randomUUID() + "@gmail.com");
    }

    @Test
    @DisplayName("on ne prend pas pour identifiant le contact principal d'un autre")
    void rejectsAPrimaryContactAlreadyHeldByAnother() {
        String bobsLogin = primaryOf(this.bob);
        this.authenticateAs(this.awa);

        assertThatThrownBy(() -> this.userService.update(this.awa.getId(), UserUpdateForm.builder()
                .firstName("Awa")
                .contacts(new HashSet<>(List.of(ContactUpdateForm.builder()
                        .id(primaryContactId(this.awa))
                        .value(bobsLogin)
                        .type(ContactType.EMAIL)
                        .isPrimary(true)
                        .build())))
                .build()))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    @DisplayName("garder son propre contact principal reste permis")
    void keepingOnesOwnPrimaryIsAllowed() {
        String myLogin = primaryOf(this.awa);
        this.authenticateAs(this.awa);

        // Le contact ne change pas de valeur : le retrouver soi-même ne doit pas passer pour un doublon.
        assertThatCode(() -> this.userService.update(this.awa.getId(), UserUpdateForm.builder()
                .firstName("Awa-Marie")
                .contacts(new HashSet<>(List.of(ContactUpdateForm.builder()
                        .id(primaryContactId(this.awa))
                        .value(myLogin)
                        .type(ContactType.EMAIL)
                        .isPrimary(true)
                        .build())))
                .build()))
                .doesNotThrowAnyException();
    }

    /* ---------- fabriques ---------- */

    private UserEntity aParentReachableAt(String email) {
        return this.userRepository.saveAndFlush(StudentParentUserEntity.builder()
                .firstName("Awa").lastName("Traoré")
                .contacts(new HashSet<>(Set.of(ContactEntity.builder()
                        .type(ContactType.EMAIL).value(email).isPrimary(true).build())))
                .build());
    }

    private static String primaryOf(UserEntity user) {
        return user.getContacts().stream()
                .filter(ContactEntity::isPrimary)
                .map(ContactEntity::getValue)
                .findFirst().orElseThrow();
    }

    private static UUID primaryContactId(UserEntity user) {
        return user.getContacts().stream()
                .filter(ContactEntity::isPrimary)
                .map(ContactEntity::getId)
                .findFirst().orElseThrow();
    }

    private void authenticateAs(UserEntity user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(primaryOf(user), "n/a", List.of()));
    }
}
