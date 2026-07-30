package com.ypyit.neoelima.domain.user;

import com.ypyit.neoelima.AbstractIntegrationTest;
import com.ypyit.neoelima.common.exception.BadRequestException;
import com.ypyit.neoelima.domain.user.entity.ContactEntity;
import com.ypyit.neoelima.domain.user.entity.PasswordEntity;
import com.ypyit.neoelima.domain.user.entity.StudentParentUserEntity;
import com.ypyit.neoelima.domain.user.enums.ContactType;
import com.ypyit.neoelima.domain.user.form.ChangePasswordForm;
import com.ypyit.neoelima.domain.user.repository.UserRepository;
import com.ypyit.neoelima.domain.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Changement de mot de passe.
 *
 * <p>Le compte visé était celui que désignait le formulaire, et non l'appelant. Deux conséquences :
 * un compte authentifié pouvait changer le mot de passe d'un autre s'il en connaissait l'actuel, et
 * la réponse distinguait « compte inconnu » de « mot de passe erroné » — de quoi énumérer les
 * comptes de la plateforme puis y tester des mots de passe.
 */
@Transactional
class ChangePasswordTest extends AbstractIntegrationTest {

    private static final String MY_PASSWORD = "Parent2026!";
    private static final String VICTIM_PASSWORD = "Victime2026!";

    @Autowired
    private UserService userService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private String myEmail;
    private String victimEmail;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        myEmail = aParentWithPassword(MY_PASSWORD);
        victimEmail = aParentWithPassword(VICTIM_PASSWORD);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(myEmail, "n/a", List.of()));
    }

    @Test
    @DisplayName("le parent change son propre mot de passe")
    void changesOwnPassword() {
        userService.changePassword(ChangePasswordForm.builder()
                .username(myEmail)
                .oldPassword(MY_PASSWORD)
                .newPassword("Nouveau2026!")
                .build());

        assertThat(passwordEncoder.matches("Nouveau2026!", passwordOf(myEmail))).isTrue();
    }

    @Test
    @DisplayName("désigner un autre compte ne le touche pas : c'est l'appelant qui est visé")
    void cannotChangeSomeoneElsesPassword() {
        // Le mot de passe fourni est bien l'actuel de la victime : seule la dérivation du compte
        // depuis le contexte de sécurité empêche le changement.
        assertThatThrownBy(() -> userService.changePassword(ChangePasswordForm.builder()
                .username(victimEmail)
                .oldPassword(VICTIM_PASSWORD)
                .newPassword("Pirate2026!")
                .build()))
                .isInstanceOf(BadRequestException.class);

        assertThat(passwordEncoder.matches(VICTIM_PASSWORD, passwordOf(victimEmail)))
                .as("le mot de passe de la victime est intact")
                .isTrue();
        assertThat(passwordEncoder.matches("Pirate2026!", passwordOf(victimEmail))).isFalse();
    }

    @Test
    @DisplayName("un compte inexistant donne la même erreur qu'un mot de passe erroné")
    void doesNotRevealWhetherAnAccountExists() {
        String unknownAccount = messageOf(() -> userService.changePassword(ChangePasswordForm.builder()
                .username("inconnu-" + UUID.randomUUID() + "@gmail.com")
                .oldPassword("Peu importe1!")
                .newPassword("Nouveau2026!")
                .build()));
        String wrongPassword = messageOf(() -> userService.changePassword(ChangePasswordForm.builder()
                .username(myEmail)
                .oldPassword("Faux2026!")
                .newPassword("Nouveau2026!")
                .build()));

        // Des messages distincts feraient de cette route un oracle d'existence de comptes.
        assertThat(unknownAccount).isEqualTo(wrongPassword);
    }

    private String passwordOf(String email) {
        return userRepository.findByPrimaryContact(email).orElseThrow().getPassword();
    }

    private static String messageOf(Runnable action) {
        try {
            action.run();
            throw new AssertionError("une exception était attendue");
        } catch (RuntimeException e) {
            return e.getMessage();
        }
    }

    private String aParentWithPassword(String rawPassword) {
        String email = "parent-" + UUID.randomUUID() + "@gmail.com";
        userRepository.saveAndFlush(StudentParentUserEntity.builder()
                .firstName("Jean").lastName("Kouassi")
                .passwordValue(PasswordEntity.builder()
                        .value(passwordEncoder.encode(rawPassword)).build())
                .contacts(primaryEmail(email)).build());
        return email;
    }

    private static Set<ContactEntity> primaryEmail(String email) {
        return new HashSet<>(List.of(ContactEntity.builder()
                .type(ContactType.EMAIL).value(email).isPrimary(true).build()));
    }
}
