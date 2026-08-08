package com.ypyit.neoelima.security;

import com.ypyit.neoelima.AbstractIntegrationTest;
import com.ypyit.neoelima.domain.establishment.dto.StudentDto;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentEntity;
import com.ypyit.neoelima.domain.establishment.repository.EstablishmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentRepository;
import com.ypyit.neoelima.domain.establishment.form.EstablishmentStudentSearchForm;
import com.ypyit.neoelima.domain.establishment.service.StudentClaimService;
import com.ypyit.neoelima.domain.establishment.service.StudentService;
import com.ypyit.neoelima.domain.user.dto.UserDto;
import com.ypyit.neoelima.domain.user.entity.ContactEntity;
import com.ypyit.neoelima.domain.user.entity.StudentParentUserEntity;
import com.ypyit.neoelima.domain.user.entity.UserEntity;
import com.ypyit.neoelima.domain.user.enums.ContactType;
import com.ypyit.neoelima.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Un couple séparé partage un enfant, pas ses coordonnées.
 *
 * <p>{@code /students/mine} sérialisait l'intégralité des {@code ContactDto} des tuteurs — téléphone,
 * courriel, WhatsApp — et leur contact principal se relisait dans {@code username}. Un parent y
 * lisait donc les coordonnées de l'autre. L'application n'a besoin que de l'identifiant du tuteur
 * pour savoir si c'est le compte courant : le reste est tu.
 */
@Transactional
class TutorContactMinimizationTest extends AbstractIntegrationTest {

    @Autowired
    private StudentClaimService studentClaimService;
    @Autowired
    private StudentService studentService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EstablishmentRepository establishmentRepository;
    @Autowired
    private StudentRepository studentRepository;

    private UserEntity awa;
    private UserEntity bob;
    private EstablishmentEntity school;
    private StudentEntity child;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        this.awa = this.aParent("Awa", "Traoré");
        this.bob = this.aParent("Bob", "Yao");
        this.aChildOf(this.awa, this.bob);
    }

    @Test
    @DisplayName("le co-tuteur apparaît par son identifiant, sans coordonnées")
    void hidesCoTutorContactsButKeepsIdentity() {
        this.authenticateAs(this.awa);

        List<StudentDto> children = this.studentClaimService.myChildrenView();

        assertThat(children).hasSize(1);
        Set<UserDto> tutors = children.getFirst().getParentUsers();
        // Les deux tuteurs sont là — l'application distingue le compte courant du co-tuteur par l'id.
        assertThat(tutors).extracting(UserDto::getId)
                .contains(this.awa.getId(), this.bob.getId());
        // Mais aucune coordonnée ne fuit, ni par la liste de contacts ni par le username.
        assertThat(tutors).allSatisfy(tutor -> {
            assertThat(tutor.getContacts()).isNull();
            assertThat(tutor.getUsername()).isNull();
        });
    }

    @Test
    @DisplayName("la recherche de rattachement ne livre pas non plus les coordonnées des tuteurs")
    void claimSearchAlsoHidesTutorContacts() {
        this.authenticateAs(this.awa);

        // `GET /students` (findByEstablishment) est la recherche d'avant-rattachement, atteinte par
        // un parent : elle rendait la fiche complète, coordonnées des tuteurs comprises.
        StudentDto found = this.studentService.findByEstablishment(
                EstablishmentStudentSearchForm.builder()
                        .establishmentId(this.school.getId())
                        .registrationNumber(this.child.getRegistrationNumber())
                        .birthDay(this.child.getBirthDay())
                        .build());

        assertThat(found.getParentUsers()).isNotEmpty();
        assertThat(found.getParentUsers()).allSatisfy(tutor -> {
            assertThat(tutor.getContacts()).isNull();
            assertThat(tutor.getUsername()).isNull();
        });
    }

    /* ---------- fabriques ---------- */

    private UserEntity aParent(String firstName, String lastName) {
        String email = firstName.toLowerCase() + "-" + UUID.randomUUID() + "@gmail.com";
        return this.userRepository.saveAndFlush(StudentParentUserEntity.builder()
                .firstName(firstName).lastName(lastName)
                .contacts(new HashSet<>(List.of(ContactEntity.builder()
                        .type(ContactType.EMAIL).value(email).isPrimary(true).build())))
                .build());
    }

    private void aChildOf(UserEntity... tutors) {
        this.school = this.establishmentRepository.saveAndFlush(
                EstablishmentEntity.builder()
                        .name("École " + UUID.randomUUID()).active(true).isPrimary(true).build());
        StudentEntity saved = this.studentRepository.saveAndFlush(StudentEntity.builder()
                .firstName("Marie").lastName("Traoré")
                .registrationNumber(UUID.randomUUID().toString().substring(0, 8))
                .birthDay(LocalDate.of(2013, 9, 12))
                .establishment(this.school).build());
        saved.setParentUsers(new HashSet<>(List.of(tutors)));
        this.child = this.studentRepository.saveAndFlush(saved);
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
