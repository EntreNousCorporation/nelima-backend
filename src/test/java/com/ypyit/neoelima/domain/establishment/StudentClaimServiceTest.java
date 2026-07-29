package com.ypyit.neoelima.domain.establishment;

import com.ypyit.neoelima.AbstractIntegrationTest;
import com.ypyit.neoelima.common.exception.BadRequestException;
import com.ypyit.neoelima.common.exception.NotFoundException;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentEntity;
import com.ypyit.neoelima.domain.establishment.form.StudentClaimForm;
import com.ypyit.neoelima.domain.establishment.repository.EstablishmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentRepository;
import com.ypyit.neoelima.domain.establishment.service.StudentClaimService;
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

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Rattachement d'un enfant par un parent.
 *
 * <p>C'est un point de sécurité : un rattachement abusif donnerait accès à la scolarité et aux
 * dettes d'un enfant qui n'est pas le sien. La preuve exigée est le triplet établissement,
 * matricule, date de naissance.
 */
@Transactional
class StudentClaimServiceTest extends AbstractIntegrationTest {

    private static final LocalDate BIRTH_DAY = LocalDate.of(2012, 4, 3);

    @Autowired
    private StudentClaimService studentClaimService;
    @Autowired
    private EstablishmentRepository establishmentRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private UserRepository userRepository;

    private EstablishmentEntity school;
    private StudentEntity student;
    private String matricule;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        school = establishmentRepository.saveAndFlush(EstablishmentEntity.builder()
                .name("École rattachement " + UUID.randomUUID()).active(true).build());
        matricule = UUID.randomUUID().toString().substring(0, 8);
        student = studentRepository.saveAndFlush(StudentEntity.builder()
                .firstName("Aaron").lastName("Koffi")
                .registrationNumber(matricule).birthDay(BIRTH_DAY)
                .establishment(school).build());
    }

    @Test
    @DisplayName("le bon triplet rattache l'enfant au compte du parent")
    void correctProofLinksTheChild() {
        authenticateAsParent();

        studentClaimService.claim(formWith(matricule, BIRTH_DAY));

        assertThat(studentClaimService.myChildren())
                .extracting(StudentEntity::getRegistrationNumber)
                .containsExactly(matricule);
    }

    @Test
    @DisplayName("une date de naissance qui ne correspond pas est refusée")
    void wrongBirthDayIsRejected() {
        authenticateAsParent();

        assertThatThrownBy(() -> studentClaimService.claim(formWith(matricule, BIRTH_DAY.plusDays(1))))
                .isInstanceOf(NotFoundException.class);

        assertThat(studentClaimService.myChildren()).isEmpty();
    }

    @Test
    @DisplayName("un matricule inconnu et une date erronée donnent le même message")
    void doesNotRevealWhetherTheRegistrationNumberExists() {
        authenticateAsParent();

        String messageMatriculeInconnu = messageOf(() ->
                studentClaimService.claim(formWith("00000000", BIRTH_DAY)));
        String messageDateFausse = messageOf(() ->
                studentClaimService.claim(formWith(matricule, BIRTH_DAY.plusYears(1))));

        // Deux messages distincts permettraient de confirmer l'existence d'un matricule par
        // tâtonnement, puis de chercher la date de naissance séparément.
        assertThat(messageMatriculeInconnu).isEqualTo(messageDateFausse);
    }

    @Test
    @DisplayName("un second rattachement du même enfant est refusé")
    void doesNotLinkTwice() {
        authenticateAsParent();
        studentClaimService.claim(formWith(matricule, BIRTH_DAY));

        assertThatThrownBy(() -> studentClaimService.claim(formWith(matricule, BIRTH_DAY)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("un compte d'établissement ne peut pas se rattacher à un élève")
    void establishmentAccountCannotClaim() {
        String email = "agent-" + UUID.randomUUID() + "@nelima.ci";
        userRepository.saveAndFlush(EstablishmentUserEntity.builder()
                .firstName("Awa").lastName("Traoré").establishment(school)
                .contacts(primaryEmail(email)).build());
        authenticate(email);

        assertThatThrownBy(() -> studentClaimService.claim(formWith(matricule, BIRTH_DAY)))
                .isInstanceOf(AccessDeniedException.class);
    }

    private StudentClaimForm formWith(String registrationNumber, LocalDate birthDay) {
        return StudentClaimForm.builder()
                .establishmentId(school.getId())
                .registrationNumber(registrationNumber)
                .birthDay(birthDay)
                .build();
    }

    private static String messageOf(Runnable action) {
        try {
            action.run();
            throw new AssertionError("une exception était attendue");
        } catch (RuntimeException e) {
            return e.getMessage();
        }
    }

    private void authenticateAsParent() {
        String email = "parent-" + UUID.randomUUID() + "@gmail.com";
        userRepository.saveAndFlush(StudentParentUserEntity.builder()
                .firstName("Jean").lastName("Kouassi")
                .contacts(primaryEmail(email)).build());
        authenticate(email);
    }

    private void authenticate(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "n/a", List.of()));
    }

    private static Set<ContactEntity> primaryEmail(String email) {
        return new HashSet<>(List.of(ContactEntity.builder()
                .type(ContactType.EMAIL).value(email).isPrimary(true).build()));
    }
}
