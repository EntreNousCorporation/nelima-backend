package com.ypyit.neoelima.domain.establishment;

import com.ypyit.neoelima.AbstractIntegrationTest;
import com.ypyit.neoelima.common.exception.BadRequestException;
import com.ypyit.neoelima.common.service.email.service.EmailService;
import com.ypyit.neoelima.domain.establishment.dto.CityDto;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.form.EstablishmentRootCreationForm;
import com.ypyit.neoelima.domain.establishment.form.EstablishmentUpdateForm;
import com.ypyit.neoelima.domain.establishment.repository.EstablishmentRepository;
import com.ypyit.neoelima.domain.establishment.service.CityService;
import com.ypyit.neoelima.domain.establishment.service.EstablishmentService;
import com.ypyit.neoelima.domain.user.dto.UserDto;
import com.ypyit.neoelima.domain.user.entity.EstablishmentUserEntity;
import com.ypyit.neoelima.domain.user.entity.RoleEntity;
import com.ypyit.neoelima.domain.user.entity.TranslateEntity;
import com.ypyit.neoelima.domain.user.enums.ContactType;
import com.ypyit.neoelima.domain.user.enums.RoleType;
import com.ypyit.neoelima.domain.user.form.ContactCreationForm;
import com.ypyit.neoelima.domain.user.form.UserSignupForm;
import com.ypyit.neoelima.domain.user.repository.RoleRepository;
import com.ypyit.neoelima.domain.user.repository.UserRepository;
import com.ypyit.neoelima.domain.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Ce qu'une école emporte de son formulaire de création.
 *
 * <p>La console demande dès la création le nom, la ville et une adresse de contact, puis affiche la
 * fiche qu'elle vient de créer. Deux de ces trois champs n'y arrivaient pas de la même façon : la
 * ville était <strong>reçue puis jetée</strong> — {@code EstablishmentRootCreationForm} ne la
 * portait pas, et Jackson ignore ce qu'il ne connaît pas —, si bien que toute école créée depuis la
 * console naissait sans ville et que le champ revenait vide à la première ouverture de la fiche.
 * Rien ne le signalait : la route répond 201.
 *
 * <p>Le contact, lui, était bien enregistré ; ce test le fixe pour que la question ne se repose
 * pas. Ce qui manquait était sa <em>lecture</em> côté console, qui cherchait un type
 * {@code PHONE} là où l'énumération dit {@code PHONE_NUMBER}.
 */
@Transactional
class PartnerCreationTest extends AbstractIntegrationTest {

    @Autowired
    private UserService userService;
    @Autowired
    private EstablishmentService establishmentService;
    @Autowired
    private CityService cityService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private EstablishmentRepository establishmentRepository;

    /** Bouchonné : créer une école expédie un courriel de bienvenue, hors sujet ici. */
    @MockitoBean
    private EmailService emailService;

    @Test
    @DisplayName("l'école créée depuis la console garde la ville qu'on lui a saisie")
    void keepsTheCityGivenAtCreation() {
        EstablishmentEntity school = this.aPartnerCreatedWith("Abidjan — Cocody");

        assertThat(school.getCity())
                .as("la ville saisie au formulaire de création, et non nulle")
                .isEqualTo("Abidjan — Cocody");
    }

    @Test
    @DisplayName("l'adresse de contact de l'école est enregistrée avec elle")
    void keepsTheSchoolContactGivenAtCreation() {
        EstablishmentEntity school = this.aPartnerCreatedWith("Sikensi");

        assertThat(school.getContacts())
                .as("le contact de l'établissement, distinct de celui du compte de direction")
                .extracting(contact -> contact.getValue())
                .contains("contact-" + school.getName() + "@ecole.ci");
    }

    @Test
    @DisplayName("une ville hors référentiel est refusée, à la création comme à la correction")
    void refusesACityOutsideTheReferential() {
        assertThatThrownBy(() -> this.aPartnerCreatedWith("Abidjan-Cocody"))
                .as("orthographe libre : c'est exactement ce que le référentiel élimine")
                .isInstanceOf(BadRequestException.class);

        EstablishmentEntity school = this.aPartnerCreatedWith("Bouaké");
        assertThatThrownBy(() -> this.establishmentService.update(school.getId(),
                EstablishmentUpdateForm.builder().city("Kinshasa").build()))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("le référentiel propose les communes d'Abidjan et les villes du pays")
    void offersTheIvorianCities() {
        List<String> labels = this.cityService.selectable().stream().map(CityDto::getLabel).toList();

        assertThat(labels)
                .contains("Abidjan — Cocody", "Abidjan — Yopougon", "Bouaké", "San-Pédro", "Korhogo")
                .as("« Abidjan » seul ne situe rien : quatre écoles sur cinq y sont")
                .doesNotContain("Abidjan");
    }

    private EstablishmentEntity aPartnerCreatedWith(String city) {
        this.ensureRootRole();
        String name = "Groupe Scolaire " + UUID.randomUUID();
        UserDto principal = this.userService.createPartnerRootUser(UserSignupForm.builder()
                .firstName("Awa")
                .lastName("Koné")
                .contacts(Set.of(ContactCreationForm.builder()
                        .value("direction-" + name + "@ecole.ci")
                        .type(ContactType.EMAIL)
                        .isPrimary(true)
                        .build()))
                .establishment(EstablishmentRootCreationForm.builder()
                        .name(name)
                        .city(city)
                        .isPrimary(true)
                        .contacts(Set.of(ContactCreationForm.builder()
                                .value("contact-" + name + "@ecole.ci")
                                .type(ContactType.EMAIL)
                                .isPrimary(true)
                                .build()))
                        .build())
                .build());

        EstablishmentUserEntity created = (EstablishmentUserEntity) this.userRepository
                .findById(principal.getId()).orElseThrow();
        return this.establishmentRepository.findById(created.getEstablishment().getId()).orElseThrow();
    }

    /** Le rôle racine d'une école, que le semis applicatif pose en production mais pas ici. */
    private void ensureRootRole() {
        this.roleRepository.findByCode(RoleType.ESTABLISHMENT_ROOT.name()).orElseGet(
                () -> this.roleRepository.saveAndFlush(RoleEntity.builder()
                        .code(RoleType.ESTABLISHMENT_ROOT.name())
                        .name(TranslateEntity.builder().fr("Direction").en("Head").build())
                        .build()));
    }
}
