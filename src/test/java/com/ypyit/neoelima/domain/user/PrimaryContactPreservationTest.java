package com.ypyit.neoelima.domain.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ypyit.neoelima.AbstractIntegrationTest;
import com.ypyit.neoelima.domain.user.dto.ContactDto;
import com.ypyit.neoelima.domain.user.dto.UserDto;
import com.ypyit.neoelima.domain.user.entity.TranslateEntity;
import com.ypyit.neoelima.domain.user.entity.ContactEntity;
import com.ypyit.neoelima.domain.user.entity.RoleEntity;
import com.ypyit.neoelima.domain.user.entity.StudentParentUserEntity;
import com.ypyit.neoelima.domain.user.entity.UserEntity;
import com.ypyit.neoelima.domain.user.enums.ContactType;
import com.ypyit.neoelima.domain.user.form.ContactCreationForm;
import com.ypyit.neoelima.domain.user.form.ContactUpdateForm;
import com.ypyit.neoelima.domain.user.form.MobileUserSignupForm;
import com.ypyit.neoelima.domain.user.form.UserUpdateForm;
import com.ypyit.neoelima.domain.user.repository.RoleRepository;
import com.ypyit.neoelima.domain.user.repository.UserRepository;
import com.ypyit.neoelima.domain.user.service.UserService;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le contact principal survit à une modification de profil.
 *
 * <p>C'est le drapeau par lequel un compte se retrouve : la connexion et le mot de passe oublié
 * cherchent tous deux l'utilisateur par son contact principal
 * ({@code UserRepository.findByPrimaryContact}, {@code where c.is_primary = true}). Le perdre ne
 * dégrade rien à l'écran — cela ferme le produit au compte, définitivement et sans message.
 *
 * <p>Il se perdait vraiment : {@code ContactServiceImpl.createOrUpdate()} refait ses entités depuis
 * les DTO qu'il vient de produire, et la correspondance {@code isPrimary} manquait dans les deux
 * sens du mapper — sans avertissement, la politique de correspondance étant permissive. Un parent
 * qui corrigeait l'orthographe de son nom ne pouvait plus se connecter.
 *
 * <p>Le cas s'écrit ici plutôt que sur le mapper : c'est le trajet complet qui cassait, et c'est
 * l'écran « Modifier le profil » de l'application parent qui l'emprunte.
 */
@Transactional
class PrimaryContactPreservationTest extends AbstractIntegrationTest {

    @Autowired
    private UserService userService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("modifier son nom ne retire pas le drapeau du contact principal")
    void renamingKeepsThePrimaryFlag() {
        String phone = "+22507" + (10_000_000 + Math.abs(UUID.randomUUID().hashCode() % 89_999_999));
        UserEntity parent = this.aParentReachableAt(phone);
        // « Modifier le profil » agit sur son propre compte : depuis que la route l'exige, le trajet
        // se joue authentifié comme le parent qu'il édite.
        this.authenticateAs(phone);

        this.userService.update(parent.getId(), UserUpdateForm.builder()
                .firstName("Didier").lastName("Kouassi-Bamba")
                .contacts(new HashSet<>(List.of(ContactUpdateForm.builder()
                        .id(this.primaryOf(parent).getId())
                        .value(phone)
                        .type(ContactType.PHONE_NUMBER)
                        .isPrimary(true)
                        .build())))
                .build());

        UserEntity reloaded = this.userRepository.findById(parent.getId()).orElseThrow();
        assertThat(reloaded.getLastName()).isEqualTo("Kouassi-Bamba");
        assertThat(reloaded.getContacts()).anyMatch(ContactEntity::isPrimary);

        // La vérification qui compte : c'est par cette requête que la connexion retrouve le compte.
        assertThat(this.userRepository.findByPrimaryContact(phone))
                .as("le compte doit rester joignable par son contact principal")
                .isPresent();
    }

    @Test
    @DisplayName("l'inscription rend un compte qui désigne son contact principal")
    void signupSaysWhichContactIsPrimary() {
        String phone = "+22505" + (10_000_000 + Math.abs(UUID.randomUUID().hashCode() % 89_999_999));
        String email = "didier-" + UUID.randomUUID() + "@email.com";
        this.ensureParentRole();

        UserDto created = this.userService.createMobileUser(MobileUserSignupForm.builder()
                .firstName("Didier").lastName("Kouassi")
                .contacts(new HashSet<>(List.of(
                        ContactCreationForm.builder().type(ContactType.PHONE_NUMBER)
                                .value(phone).isPrimary(true).whatsApp(false).build(),
                        ContactCreationForm.builder().type(ContactType.EMAIL)
                                .value(email).isPrimary(false).whatsApp(false).build())))
                .build());

        // L'application y lit sous quel identifiant le parent vient de s'inscrire — c'est celui-là
        // qui recevra le code, et celui-là seul qui ouvrira la connexion. La réponse les donnait
        // tous deux secondaires : trois mappeurs recopiaient la même conversion incomplète.
        assertThat(created.getContacts())
                .as("un compte a toujours exactement un contact principal")
                .filteredOn(ContactDto::isPrimary)
                .extracting(ContactDto::getValue)
                .containsExactly(phone);
    }

    @Test
    @DisplayName("le contact se rend sous le nom qu'il accepte à l'écriture")
    void theWireNameIsTheSameBothWays() throws Exception {
        String json = this.objectMapper.writeValueAsString(ContactDto.builder()
                .type(ContactType.PHONE_NUMBER).value("+2250702334567").isPrimary(true).build());

        // Le lecteur que Lombok produit — `isPrimary()` — faisait rendre « primary », quand les
        // formulaires acceptent « isPrimary ». Envoyer et recevoir sous deux noms oblige chaque
        // appelant à connaître l'asymétrie ; le premier qui l'ignore lit un compte sans contact
        // principal, et l'application mobile ne sait plus sous quel identifiant il se connecte.
        assertThat(json).contains("\"isPrimary\":true").doesNotContain("\"primary\"");
    }

    /// Le rôle du parent, que le semis applicatif pose en production mais pas dans la base du test.
    private void ensureParentRole() {
        this.roleRepository.findByCode("STUDENT_PARENT").orElseGet(
                () -> this.roleRepository.saveAndFlush(RoleEntity.builder()
                        .code("STUDENT_PARENT")
                        .name(TranslateEntity.builder().fr("Parent").en("Parent").build())
                        .build()));
    }

    private void authenticateAs(String primaryContact) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(primaryContact, "n/a", List.of()));
    }

    private ContactEntity primaryOf(UserEntity user) {
        return user.getContacts().stream()
                .filter(ContactEntity::isPrimary)
                .findFirst().orElseThrow();
    }

    private UserEntity aParentReachableAt(String phone) {
        return this.userRepository.saveAndFlush(StudentParentUserEntity.builder()
                .firstName("Didier").lastName("Kouassi")
                .contacts(new HashSet<>(Set.of(ContactEntity.builder()
                        .type(ContactType.PHONE_NUMBER).value(phone)
                        .isPrimary(true).build())))
                .build());
    }
}
