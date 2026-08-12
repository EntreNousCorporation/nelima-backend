package com.ypyit.neoelima.domain.user;

import com.ypyit.neoelima.AbstractIntegrationTest;
import com.ypyit.neoelima.common.exception.BadRequestException;
import com.ypyit.neoelima.common.service.email.enums.EmailTemplateType;
import com.ypyit.neoelima.common.service.email.service.EmailConstants;
import com.ypyit.neoelima.common.service.email.service.EmailService;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.repository.EstablishmentRepository;
import com.ypyit.neoelima.domain.user.entity.AdminUserEntity;
import com.ypyit.neoelima.domain.user.entity.ContactEntity;
import com.ypyit.neoelima.domain.user.entity.EstablishmentUserEntity;
import com.ypyit.neoelima.domain.user.enums.ContactType;
import com.ypyit.neoelima.domain.user.form.InitResetPasswordForm;
import com.ypyit.neoelima.domain.user.repository.UserRepository;
import com.ypyit.neoelima.domain.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.context.Context;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

/**
 * Où mène le lien de définition de mot de passe.
 *
 * <p>Rien ne vérifiait cette adresse. Elle valait
 * {@code https://oauthdebugger.com/} — un site tiers de débogage — dans {@code application.yml},
 * <strong>en dur, sans espace réservé</strong> : aucune variable d'environnement ne pouvait la
 * rattraper. Chaque courriel de bienvenue et de mot de passe oublié partait donc en production avec
 * l'identifiant du destinataire et un jeton de réinitialisation valide, en paramètres d'URL, vers
 * un domaine que nous ne tenons pas.
 *
 * <p>Le défaut a tenu treize jours et cinq comptes parce qu'il ne casse rien de visible côté
 * serveur : le courriel part, la route répond 201, et seul celui qui clique découvre qu'il n'arrive
 * nulle part. C'est ce que ce test rend impossible à reproduire.
 *
 * <p>La cible est éprouvée <strong>des deux côtés</strong> — école et administration —, car la
 * seconde branche n'était jamais prise : {@code initResetPassword} ne posait aucune cible, et
 * {@code RoleTarget.ADMIN.equals(null)} vaut faux.
 */
@Transactional
class ActivationLinkTest extends AbstractIntegrationTest {

    @Autowired
    private UserService userService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EstablishmentRepository establishmentRepository;

    /** Bouchonné pour lire le lien tel qu'il partirait, sans expédier quoi que ce soit. */
    @MockitoBean
    private EmailService emailService;

    @Test
    @DisplayName("le lien d'un compte d'école mène au portail des écoles, avec identifiant et jeton")
    void partnerLinkPointsToThePartnerPortal() {
        EstablishmentUserEntity agent = aSchoolAgent(aSchool());

        this.userService.resendActivationLink(agent.getEstablishment().getId(), agent.getId());

        String link = capturedLink(EmailTemplateType.WELCOME_USER);
        assertThat(link)
                .as("le portail des écoles, et non un site de débogage tiers")
                .startsWith("https://partenaire.nelima.ci/connexion/reset")
                .contains("username=" + agent.getUsername().replace("@", "%40"))
                .contains("token=");
    }

    @Test
    @DisplayName("le lien d'un compte YPYit mène au back-office, et non au portail des écoles")
    void adminLinkPointsToTheBackOffice() {
        AdminUserEntity admin = this.userRepository.saveAndFlush(AdminUserEntity.builder()
                .firstName("Awa").lastName("Koné")
                .contacts(primaryEmail("admin-" + UUID.randomUUID() + "@ypy-it.com"))
                .build());

        this.userService.initResetPassword(InitResetPasswordForm.builder()
                .username(admin.getUsername()).build());

        assertThat(capturedLink(EmailTemplateType.RESET_PASSWORD))
                .startsWith("https://admin.nelima.ci/connexion/reset");
    }

    @Test
    @DisplayName("on ne renvoie pas le lien d'un compte qui appartient à une autre école")
    void refusesToResendForAnotherSchoolsAccount() {
        EstablishmentUserEntity agent = aSchoolAgent(aSchool());
        EstablishmentEntity otherSchool = aSchool();

        // Le garde du contrôleur ne prouve qu'une chose : que l'appelant administre l'école citée
        // dans l'URL. Sans cette vérification-ci, l'identifiant d'un compte quelconque suffirait à
        // lui faire expédier un lien de mot de passe valide.
        assertThatThrownBy(() -> this.userService.resendActivationLink(otherSchool.getId(), agent.getId()))
                .isInstanceOf(BadRequestException.class);
    }

    private String capturedLink(EmailTemplateType type) {
        ArgumentCaptor<Context> captor = ArgumentCaptor.forClass(Context.class);
        verify(this.emailService).send(captor.capture(), org.mockito.ArgumentMatchers.eq(type));
        return String.valueOf(captor.getValue().getVariable(EmailConstants.TOKEN_LINK));
    }

    private EstablishmentEntity aSchool() {
        return this.establishmentRepository.saveAndFlush(EstablishmentEntity.builder()
                .name("École " + UUID.randomUUID()).active(true).build());
    }

    private EstablishmentUserEntity aSchoolAgent(EstablishmentEntity school) {
        return this.userRepository.saveAndFlush(EstablishmentUserEntity.builder()
                .firstName("Aya").lastName("Traoré")
                .establishment(school)
                .contacts(primaryEmail("agent-" + UUID.randomUUID() + "@email.com"))
                .build());
    }

    private Set<ContactEntity> primaryEmail(String email) {
        return new HashSet<>(Set.of(ContactEntity.builder()
                .type(ContactType.EMAIL).value(email).isPrimary(true).build()));
    }
}
