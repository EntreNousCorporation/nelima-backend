package com.ypyit.neoelima.domain.establishment;

import com.ypyit.neoelima.AbstractIntegrationTest;
import com.ypyit.neoelima.domain.establishment.dto.EstablishmentContactCardDto;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentEntity;
import com.ypyit.neoelima.domain.establishment.repository.EstablishmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentRepository;
import com.ypyit.neoelima.domain.establishment.service.ParentSchoolContactService;
import com.ypyit.neoelima.domain.user.entity.ContactEntity;
import com.ypyit.neoelima.domain.user.entity.StudentParentUserEntity;
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

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * De quoi joindre l'école de son enfant.
 *
 * <p>Ces coordonnées existaient déjà mais restaient hors de portée d'un parent. Les ouvrir sans
 * portée ferait de l'application un annuaire des établissements du pays, numéros de direction
 * compris — c'est le seul risque de cette route, et il est éprouvé ici.
 */
@Transactional
class ParentSchoolContactTest extends AbstractIntegrationTest {

    @Autowired
    private ParentSchoolContactService service;
    @Autowired
    private EstablishmentRepository establishmentRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private UserRepository userRepository;

    private EstablishmentEntity mySchool;
    private EstablishmentEntity otherSchool;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        this.mySchool = this.aSchool("Institut LKM");
        this.otherSchool = this.aSchool("EPV les Collinettes");
    }

    @Test
    @DisplayName("un parent obtient les coordonnées de l'école de son enfant")
    void servesTheSchoolOfMyChild() {
        UserEntity parent = this.aParentOf(this.mySchool);
        this.authenticateAs(parent);

        EstablishmentContactCardDto card = this.service.contactCardOf(this.mySchool.getId());

        assertThat(card.getName()).isEqualTo(this.mySchool.getName());
        assertThat(card.getContacts()).isNotEmpty();
        // Le drapeau WhatsApp décide si l'application propose une conversation plutôt qu'un appel :
        // rien ne permettrait de le deviner d'un numéro.
        assertThat(card.getContacts()).anyMatch(contact -> contact.isWhatsApp());
        // Le contact principal en tête : c'est celui que l'école a désigné comme joignable.
        assertThat(card.getContacts().getFirst().isPrimary()).isTrue();
    }

    @Test
    @DisplayName("l'école d'une autre famille reste fermée")
    void refusesASchoolThatIsNotMine() {
        UserEntity parent = this.aParentOf(this.mySchool);
        this.authenticateAs(parent);

        assertThatThrownBy(() -> this.service.contactCardOf(this.otherSchool.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("un compte sans enfant n'ouvre aucune fiche")
    void refusesAnAccountWithoutChildren() {
        this.authenticateAs(this.aParentOf(null));

        // C'est l'état d'un compte fraîchement créé — et la porte par laquelle un annuaire
        // s'échapperait si la portée venait du paramètre plutôt que des enfants rattachés.
        assertThatThrownBy(() -> this.service.contactCardOf(this.mySchool.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    /* ---------- fabriques ---------- */

    private EstablishmentEntity aSchool(String name) {
        EstablishmentEntity school = EstablishmentEntity.builder()
                .name(name + " " + UUID.randomUUID())
                .webSite("https://exemple.ci")
                // Un libellé du référentiel : la colonne y est désormais rattachée par clé
                // étrangère, et « Abidjan » seul n'en fait pas partie — le district est éclaté en
                // communes, sans quoi la ville ne situerait plus rien dans une liste.
                .city("Abidjan — Cocody")
                .active(true)
                .contacts(new HashSet<>(List.of(
                        ContactEntity.builder().type(ContactType.PHONE_NUMBER)
                                .value("+2252720451208").isPrimary(true).whatsApp(true).build(),
                        ContactEntity.builder().type(ContactType.EMAIL)
                                .value("secretariat-" + UUID.randomUUID() + "@exemple.ci")
                                .isPrimary(false).build())))
                .build();
        return this.establishmentRepository.saveAndFlush(school);
    }

    private UserEntity aParentOf(EstablishmentEntity school) {
        String email = "parent-" + UUID.randomUUID() + "@gmail.com";
        StudentParentUserEntity parent = this.userRepository.saveAndFlush(
                StudentParentUserEntity.builder()
                        .firstName("Didier").lastName("Kouassi")
                        .contacts(new HashSet<>(List.of(ContactEntity.builder()
                                .type(ContactType.EMAIL).value(email).isPrimary(true).build())))
                        .build());
        if (school == null) {
            return parent;
        }

        StudentEntity child = this.studentRepository.saveAndFlush(StudentEntity.builder()
                .firstName("Marie").lastName("Kouassi")
                .registrationNumber(UUID.randomUUID().toString().substring(0, 8))
                .birthDay(LocalDate.of(2013, 9, 12))
                .establishment(school).build());
        Set<UserEntity> parents = new HashSet<>(child.getParentUsers());
        parents.add(parent);
        child.setParentUsers(parents);
        this.studentRepository.saveAndFlush(child);

        return parent;
    }

    private void authenticateAs(UserEntity user) {
        String email = user.getContacts().stream()
                .filter(ContactEntity::isPrimary)
                .map(ContactEntity::getValue)
                .findFirst().orElseThrow();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, "n/a", List.of()));
    }
}
