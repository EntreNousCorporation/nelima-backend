package com.ypyit.neoelima.domain.establishment;

import com.ypyit.neoelima.AbstractIntegrationTest;
import com.ypyit.neoelima.common.exception.BadRequestException;
import com.ypyit.neoelima.domain.establishment.dto.AcademicYearDto;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentEntity;
import com.ypyit.neoelima.domain.establishment.form.AcademicYearForm;
import com.ypyit.neoelima.domain.establishment.form.StudentCreationForm;
import com.ypyit.neoelima.domain.establishment.repository.AcademicYearRepository;
import com.ypyit.neoelima.domain.establishment.repository.EstablishmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.LevelOfStudyRepository;
import com.ypyit.neoelima.domain.establishment.entity.LevelOfStudyEntity;
import com.ypyit.neoelima.domain.establishment.service.AcademicYearContext;
import com.ypyit.neoelima.domain.establishment.service.AcademicYearService;
import com.ypyit.neoelima.domain.establishment.service.StudentService;
import com.ypyit.neoelima.domain.establishment.form.StudentSearchForm;
import com.ypyit.neoelima.domain.user.entity.AdminUserEntity;
import com.ypyit.neoelima.domain.user.entity.ContactEntity;
import com.ypyit.neoelima.domain.user.entity.EstablishmentUserEntity;
import com.ypyit.neoelima.domain.user.entity.TranslateEntity;
import com.ypyit.neoelima.domain.user.enums.ContactType;
import com.ypyit.neoelima.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
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
 * Années scolaires de la plateforme.
 *
 * <p>La plateforme définit les années pour toutes les écoles ; chaque école y pose ses trimestres
 * sans toucher aux autres ; chaque utilisateur choisit l'année qu'il consulte sans faire basculer
 * ses collègues.
 */
@Transactional
class AcademicYearServiceTest extends AbstractIntegrationTest {

    @Autowired
    private AcademicYearService academicYearService;
    @Autowired
    private AcademicYearContext academicYearContext;
    @Autowired
    private StudentService studentService;
    @Autowired
    private AcademicYearRepository academicYearRepository;
    @Autowired
    private EstablishmentRepository establishmentRepository;
    @Autowired
    private LevelOfStudyRepository levelOfStudyRepository;
    @Autowired
    private UserRepository userRepository;

    private String adminEmail;
    private EstablishmentEntity school;
    private String directorEmail;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        this.adminEmail = "admin-" + UUID.randomUUID() + "@ypyit.com";
        this.userRepository.saveAndFlush(AdminUserEntity.builder().firstName("Plate").lastName("Forme")
                .contacts(this.email(this.adminEmail)).build());
        this.school = this.school();
        this.directorEmail = this.director(this.school);
    }

    @Test
    @DisplayName("une école ne déclare pas d'année : c'est la plateforme qui les définit")
    void schoolsCannotDeclareYears() {
        this.login(this.directorEmail);

        assertThatThrownBy(() -> this.academicYearService.create(this.form("2026-2027", 2026, true)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("désigner une année en cours retire ce statut à la précédente")
    void onlyOneCurrentYear() {
        this.login(this.adminEmail);
        AcademicYearDto first = this.academicYearService.create(this.form("2025-2026", 2025, true));
        AcademicYearDto second = this.academicYearService.create(this.form("2026-2027", 2026, true));

        assertThat(this.academicYearRepository.findById(UUID.fromString(first.getId())).orElseThrow().isActive()).isFalse();
        assertThat(this.academicYearContext.currentId()).isEqualTo(UUID.fromString(second.getId()));
    }

    @Test
    @DisplayName("chaque école pose ses trimestres, sans toucher à ceux des autres")
    void periodsBelongToEachSchool() {
        this.login(this.adminEmail);
        UUID year = UUID.fromString(this.academicYearService.create(this.form("2026-2027", 2026, true)).getId());
        EstablishmentEntity other = this.school();
        String otherDirector = this.director(other);

        this.login(this.directorEmail);
        this.academicYearService.definePeriods(year, List.of(
                this.period("1er trimestre", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 18))));
        this.login(otherDirector);
        this.academicYearService.definePeriods(year, List.of(
                this.period("1er trimestre", LocalDate.of(2026, 9, 14), LocalDate.of(2026, 11, 30)),
                this.period("2e trimestre", LocalDate.of(2026, 12, 1), LocalDate.of(2027, 2, 26))));

        this.login(this.directorEmail);
        assertThat(this.yearAsSeen(year).getPeriods()).hasSize(1);
        this.login(otherDirector);
        assertThat(this.yearAsSeen(year).getPeriods()).hasSize(2);
    }

    @Test
    @DisplayName("un trimestre hors des bornes de l'année est refusé")
    void periodsStayWithinTheYear() {
        this.login(this.adminEmail);
        UUID year = UUID.fromString(this.academicYearService.create(this.form("2026-2027", 2026, true)).getId());
        this.login(this.directorEmail);

        assertThatThrownBy(() -> this.academicYearService.definePeriods(year, List.of(
                this.period("Été", LocalDate.of(2027, 7, 1), LocalDate.of(2027, 8, 31)))))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("une année où une école travaille ne se supprime plus, ni ne se resserre sur ses trimestres")
    void yearsInUseAreProtected() {
        this.login(this.adminEmail);
        UUID previous = UUID.fromString(this.academicYearService.create(this.form("2025-2026", 2025, true)).getId());
        this.academicYearService.create(this.form("2026-2027", 2026, true));
        this.login(this.directorEmail);
        this.academicYearService.definePeriods(previous, List.of(
                this.period("3e trimestre", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 7, 10))));

        this.login(this.adminEmail);
        assertThatThrownBy(() -> this.academicYearService.delete(previous)).isInstanceOf(BadRequestException.class);
        AcademicYearForm shrunk = this.form("2025-2026", 2025, false);
        shrunk.setEndDate(LocalDate.of(2026, 6, 30));
        assertThatThrownBy(() -> this.academicYearService.update(previous, shrunk)).isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("l'année consultée est propre à chaque utilisateur, et ses écrans la suivent")
    void consultedYearIsPersonal() {
        this.login(this.adminEmail);
        UUID previous = UUID.fromString(this.academicYearService.create(this.form("2025-2026", 2025, true)).getId());
        UUID current = UUID.fromString(this.academicYearService.create(this.form("2026-2027", 2026, true)).getId());
        String secretary = this.director(this.school);

        // Un élève inscrit cette année.
        this.login(this.directorEmail);
        LevelOfStudyEntity level = this.levelOfStudyRepository.saveAndFlush(LevelOfStudyEntity.builder()
                .code("CP1-" + UUID.randomUUID().toString().substring(0, 6)).position(1)
                .name(TranslateEntity.builder().fr("CP1").en("CP1").build()).build());
        this.school.getLevelOfStudies().add(level);
        this.establishmentRepository.saveAndFlush(this.school);
        StudentCreationForm form = new StudentCreationForm();
        form.setFirstName("Yao");
        form.setLastName("KOUAME");
        form.setRegistrationNumber("2026001");
        form.setBirthDay(LocalDate.of(2019, 5, 2));
        form.setPlaceOfBirth("Abidjan");
        form.setLevelOfStudyCode(level.getCode());
        this.studentService.create(form);

        // La direction revoit l'année passée : elle n'y trouve pas cet élève.
        this.academicYearService.consult(previous);
        assertThat(this.academicYearContext.consultedId()).isEqualTo(previous);
        assertThat(this.studentService.search(new StudentSearchForm(), PageRequest.of(0, 20)).getContent()).isEmpty();

        // Le secrétariat, lui, est resté sur l'année en cours.
        this.login(secretary);
        assertThat(this.academicYearContext.consultedId()).isEqualTo(current);
        assertThat(this.studentService.search(new StudentSearchForm(), PageRequest.of(0, 20)).getContent()).hasSize(1);
    }

    private AcademicYearDto yearAsSeen(UUID id) {
        return this.academicYearService.findAll().stream()
                .filter(year -> year.getId().equals(id.toString())).findFirst().orElseThrow();
    }

    private AcademicYearForm form(String label, int startYear, boolean active) {
        AcademicYearForm form = new AcademicYearForm();
        form.setLabel(label);
        form.setStartDate(LocalDate.of(startYear, 9, 1));
        form.setEndDate(LocalDate.of(startYear + 1, 7, 31));
        form.setActive(active);
        return form;
    }

    private AcademicYearForm.AcademicPeriodForm period(String label, LocalDate from, LocalDate to) {
        AcademicYearForm.AcademicPeriodForm period = new AcademicYearForm.AcademicPeriodForm();
        period.setLabel(label);
        period.setStartDate(from);
        period.setEndDate(to);
        return period;
    }

    private EstablishmentEntity school() {
        return this.establishmentRepository.saveAndFlush(EstablishmentEntity.builder()
                .name("École " + UUID.randomUUID()).active(true).isPrimary(true).build());
    }

    private String director(EstablishmentEntity establishment) {
        String email = "direction-" + UUID.randomUUID() + "@ecole.ci";
        this.userRepository.saveAndFlush(EstablishmentUserEntity.builder()
                .firstName("Awa").lastName("Traoré").establishment(establishment)
                .contacts(this.email(email)).build());
        return email;
    }

    private Set<ContactEntity> email(String email) {
        return new HashSet<>(Set.of(ContactEntity.builder()
                .type(ContactType.EMAIL).value(email).isPrimary(true).build()));
    }

    private void login(String email) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, "n/a", List.of()));
    }
}
