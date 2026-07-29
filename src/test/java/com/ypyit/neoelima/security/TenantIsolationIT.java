package com.ypyit.neoelima.security;

import com.ypyit.neoelima.domain.establishment.dto.StudentDto;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentEntity;
import com.ypyit.neoelima.domain.establishment.form.StudentSearchForm;
import com.ypyit.neoelima.domain.establishment.repository.EstablishmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentRepository;
import com.ypyit.neoelima.domain.establishment.service.StudentService;
import com.ypyit.neoelima.domain.user.entity.ContactEntity;
import com.ypyit.neoelima.domain.user.entity.EstablishmentUserEntity;
import com.ypyit.neoelima.domain.user.entity.StudentParentUserEntity;
import com.ypyit.neoelima.domain.user.entity.UserEntity;
import com.ypyit.neoelima.domain.user.enums.ContactType;
import com.ypyit.neoelima.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test anti-régression de l'isolation multi-établissements.
 *
 * <p>Avant l'introduction de {@code CurrentUserProvider}, les services de recherche filtraient sur
 * l'{@code establishmentId} fourni par le client : un utilisateur d'établissement authentifié
 * pouvait lire les élèves d'une autre école en changeant le paramètre. Ces tests échouent si cette
 * dérivation depuis le principal est retirée.
 */
@Testcontainers
@SpringBootTest
@Transactional
class TenantIsolationIT {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.4-alpine")
            .withDatabaseName("nelima_test")
            .withUsername("nelima")
            .withPassword("nelima");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("nelima.initialize-data", () -> "false");
    }

    @Autowired
    private StudentService studentService;
    @Autowired
    private EstablishmentRepository establishmentRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private UserRepository userRepository;

    private EstablishmentEntity victorLoba;
    private EstablishmentEntity sainteMarie;
    private StudentEntity aaronAtVictorLoba;
    private StudentEntity fatouAtSainteMarie;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();

        victorLoba = establishmentRepository.save(EstablishmentEntity.builder()
                .name("Victor Loba Abobo").active(true).build());
        sainteMarie = establishmentRepository.save(EstablishmentEntity.builder()
                .name("Sainte Marie Cocody").active(true).build());

        aaronAtVictorLoba = studentRepository.save(StudentEntity.builder()
                .firstName("Aaron").lastName("Koffi").registrationNumber("2022333")
                .birthDay(LocalDate.of(2012, 4, 3)).establishment(victorLoba).build());
        fatouAtSainteMarie = studentRepository.save(StudentEntity.builder()
                .firstName("Fatou").lastName("Diallo").registrationNumber("2022444")
                .birthDay(LocalDate.of(2011, 9, 21)).establishment(sainteMarie).build());
    }

    @Test
    @DisplayName("un utilisateur d'établissement ne voit que les élèves de son établissement")
    void schoolUserOnlySeesOwnStudents() {
        authenticateAs(saveSchoolUser("secretaire@victorloba.ci", victorLoba));

        Page<StudentDto> result = studentService.search(
                StudentSearchForm.builder().build(), PageRequest.of(0, 50));

        assertThat(result.getContent())
                .extracting(StudentDto::getRegistrationNumber)
                .containsExactly(aaronAtVictorLoba.getRegistrationNumber());
    }

    @Test
    @DisplayName("cibler explicitement un autre établissement ne contourne pas le filtre")
    void schoolUserCannotTargetAnotherEstablishment() {
        authenticateAs(saveSchoolUser("secretaire@victorloba.ci", victorLoba));

        // La faille corrigée : cet identifiant était repris tel quel dans la requête.
        Page<StudentDto> result = studentService.search(
                StudentSearchForm.builder().establishmentId(sainteMarie.getId()).build(),
                PageRequest.of(0, 50));

        assertThat(result.getContent())
                .as("l'établissement demandé doit être ignoré au profit de celui du principal")
                .extracting(StudentDto::getRegistrationNumber)
                .containsExactly(aaronAtVictorLoba.getRegistrationNumber())
                .doesNotContain(fatouAtSainteMarie.getRegistrationNumber());
    }

    @Test
    @DisplayName("un parent n'a pas de portée établissement et ne peut pas lister d'élèves")
    void parentCannotBrowseEstablishmentStudents() {
        authenticateAs(saveParentUser("parent@gmail.com"));

        assertThatThrownBy(() -> studentService.search(
                StudentSearchForm.builder().establishmentId(victorLoba.getId()).build(),
                PageRequest.of(0, 50)))
                .isInstanceOf(AccessDeniedException.class);
    }

    private EstablishmentUserEntity saveSchoolUser(String email, EstablishmentEntity establishment) {
        return userRepository.save(EstablishmentUserEntity.builder()
                .firstName("Awa").lastName("Traoré")
                .establishment(establishment)
                .contacts(primaryEmail(email))
                .build());
    }

    private StudentParentUserEntity saveParentUser(String email) {
        return userRepository.save(StudentParentUserEntity.builder()
                .firstName("Jean").lastName("Kouassi")
                .contacts(primaryEmail(email))
                .build());
    }

    private static Set<ContactEntity> primaryEmail(String email) {
        return Set.of(ContactEntity.builder()
                .type(ContactType.EMAIL).value(email).isPrimary(true).build());
    }

    private void authenticateAs(UserEntity user) {
        String username = user.getContacts().iterator().next().getValue();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "n/a", List.of()));
    }
}
