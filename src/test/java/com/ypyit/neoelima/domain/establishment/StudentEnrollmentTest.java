package com.ypyit.neoelima.domain.establishment;

import com.ypyit.neoelima.AbstractIntegrationTest;
import com.ypyit.neoelima.common.exception.BadRequestException;
import com.ypyit.neoelima.domain.establishment.dto.StudentDto;
import com.ypyit.neoelima.domain.establishment.dto.StudentStatsDto;
import com.ypyit.neoelima.domain.establishment.entity.AcademicYearEntity;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.FeeEntity;
import com.ypyit.neoelima.domain.establishment.entity.InstallmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.LevelOfStudyEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentEnrollmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentEntity;
import com.ypyit.neoelima.domain.establishment.form.StudentCreationForm;
import com.ypyit.neoelima.domain.establishment.form.StudentSearchForm;
import com.ypyit.neoelima.domain.establishment.repository.AcademicYearRepository;
import com.ypyit.neoelima.domain.establishment.repository.EstablishmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.FeeRepository;
import com.ypyit.neoelima.domain.establishment.repository.InstallmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.LevelOfStudyRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentEnrollmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentRepository;
import com.ypyit.neoelima.domain.establishment.service.AcademicYearService;
import com.ypyit.neoelima.domain.establishment.service.StudentCsvImporter;
import com.ypyit.neoelima.domain.establishment.service.StudentService;
import com.ypyit.neoelima.domain.establishment.service.StudentStatsService;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Inscription annuelle des élèves.
 *
 * <p>Un élève naît inscrit dans l'année en cours, qu'il soit saisi à la main ou importé ; l'écran
 * Élèves décrit une année ; l'année précédente sert de point de comparaison.
 */
@Transactional
class StudentEnrollmentTest extends AbstractIntegrationTest {

    @Autowired
    private StudentService studentService;
    @Autowired
    private StudentCsvImporter importer;
    @Autowired
    private StudentStatsService studentStatsService;
    @Autowired
    private AcademicYearService academicYearService;
    @Autowired
    private EstablishmentRepository establishmentRepository;
    @Autowired
    private LevelOfStudyRepository levelOfStudyRepository;
    @Autowired
    private AcademicYearRepository academicYearRepository;
    @Autowired
    private StudentEnrollmentRepository studentEnrollmentRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private FeeRepository feeRepository;
    @Autowired
    private InstallmentRepository installmentRepository;
    @Autowired
    private UserRepository userRepository;

    private EstablishmentEntity school;
    private LevelOfStudyEntity level;
    private AcademicYearEntity previousYear;
    private AcademicYearEntity currentYear;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        this.level = this.levelOfStudyRepository.saveAndFlush(LevelOfStudyEntity.builder()
                .code("CP1-" + UUID.randomUUID().toString().substring(0, 6)).position(1)
                .name(TranslateEntity.builder().fr("CP1").en("CP1").build()).build());
        this.school = this.establishmentRepository.saveAndFlush(EstablishmentEntity.builder()
                .name("École " + UUID.randomUUID()).active(true).isPrimary(true).build());
        this.school.getLevelOfStudies().add(this.level);
        this.school = this.establishmentRepository.saveAndFlush(this.school);
        this.previousYear = this.year("2025-2026", 2025, false);
        this.currentYear = this.year("2026-2027", 2026, true);
        this.authenticateOn(this.school);
    }

    @Test
    @DisplayName("un élève inscrit à la main l'est dans l'année en cours, avec son niveau")
    void manualRegistrationEnrollsInTheActiveYear() {
        StudentDto created = this.studentService.create(this.form("2026001"));

        StudentEnrollmentEntity enrollment = this.studentEnrollmentRepository
                .findByStudent_IdAndAcademicYear_Id(created.getId(), this.currentYear.getId()).orElseThrow();
        assertThat(enrollment.getLevelOfStudy().getCode()).isEqualTo(this.level.getCode());
    }

    @Test
    @DisplayName("un élève importé reçoit ses frais avec leurs tranches, et son inscription")
    void importedStudentsOweTheirFeesAndAreEnrolled() {
        // Le retour : l'import ne rattachait aucun frais, les élèves importés ne devaient rien.
        FeeEntity fee = this.feeRepository.saveAndFlush(FeeEntity.builder()
                .name("Scolarité " + UUID.randomUUID()).price(new BigDecimal("120000"))
                .academical(true).establishment(this.school).build());
        fee.getLevelOfStudies().add(this.level);
        this.feeRepository.saveAndFlush(fee);

        this.importer.importFrom(new MockMultipartFile("file", "eleves.csv", "text/csv",
                ("matricule;nom;prenom;date_naissance;lieu_naissance;niveau\n"
                        + "2026101;KONE;Awa;03-04-2019;Abidjan;" + this.level.getCode() + "\n")
                        .getBytes(StandardCharsets.UTF_8)));

        StudentEntity imported = this.studentRepository
                .findByEstablishment_IdAndRegistrationNumber(this.school.getId(), "2026101").orElseThrow();
        List<InstallmentEntity> installments = this.installmentRepository.findAll().stream()
                .filter(line -> line.getStudentFee().getStudent().getId().equals(imported.getId()))
                .toList();
        assertThat(installments).hasSize(1);
        assertThat(installments.getFirst().getAmount()).isEqualByComparingTo("120000");
        assertThat(this.studentEnrollmentRepository
                .findByStudent_IdAndAcademicYear_Id(imported.getId(), this.currentYear.getId())).isPresent();
    }

    @Test
    @DisplayName("l'écran décrit une année, et se compare à la précédente")
    void statsDescribeOneYearAndCompareToThePrevious() {
        // Deux élèves l'an dernier, dont un seul réinscrit ; deux nouveaux cette année.
        StudentEntity stayed = this.student("2025001");
        StudentEntity left = this.student("2025002");
        this.enroll(stayed, this.previousYear);
        this.enroll(left, this.previousYear);
        this.enroll(stayed, this.currentYear);
        this.studentService.create(this.form("2026002"));
        this.studentService.create(this.form("2026003"));

        StudentStatsDto current = this.studentStatsService.compute(null);
        assertThat(current.getAcademicYearLabel()).isEqualTo("2026-2027");
        assertThat(current.getTotal()).isEqualTo(3);
        assertThat(current.getPreviousYearTotal()).isEqualTo(2);
        assertThat(current.getTotalDeltaPercent()).isEqualTo(50.0);

        StudentStatsDto past = this.studentStatsService.compute(this.previousYear.getId());
        assertThat(past.getTotal()).isEqualTo(2);
        assertThat(past.getPreviousYearTotal()).isNull();

        assertThat(this.studentService.search(StudentSearchForm.builder()
                        .academicYearId(this.previousYear.getId()).build(), PageRequest.of(0, 20))
                .getContent()).extracting(StudentDto::getRegistrationNumber)
                .containsExactlyInAnyOrder("2025001", "2025002");
    }

    @Test
    @DisplayName("une année qui a des inscrits ne se supprime pas")
    void aYearWithEnrollmentsCannotBeDeleted() {
        this.enroll(this.student("2025003"), this.previousYear);

        assertThatThrownBy(() -> this.academicYearService.delete(this.previousYear.getId()))
                .isInstanceOf(BadRequestException.class);
    }

    private StudentCreationForm form(String registrationNumber) {
        StudentCreationForm form = new StudentCreationForm();
        form.setFirstName("Yao");
        form.setLastName("KOUAME");
        form.setRegistrationNumber(registrationNumber);
        form.setBirthDay(LocalDate.of(2019, 5, 2));
        form.setPlaceOfBirth("Abidjan");
        form.setLevelOfStudyCode(this.level.getCode());
        return form;
    }

    private StudentEntity student(String registrationNumber) {
        return this.studentRepository.saveAndFlush(StudentEntity.builder()
                .firstName("Ama").lastName("TANOH").registrationNumber(registrationNumber)
                .levelOfStudy(this.level).establishment(this.school).build());
    }

    private void enroll(StudentEntity student, AcademicYearEntity year) {
        this.studentEnrollmentRepository.saveAndFlush(StudentEnrollmentEntity.builder()
                .student(student).academicYear(year).levelOfStudy(this.level).build());
    }

    private AcademicYearEntity year(String label, int startYear, boolean active) {
        return this.academicYearRepository.saveAndFlush(AcademicYearEntity.builder()
                .label(label).startDate(LocalDate.of(startYear, 9, 1))
                .endDate(LocalDate.of(startYear + 1, 7, 31)).active(active)
                .establishment(this.school).build());
    }

    private void authenticateOn(EstablishmentEntity establishment) {
        String email = "direction-" + UUID.randomUUID() + "@ecole.ci";
        this.userRepository.saveAndFlush(EstablishmentUserEntity.builder()
                .firstName("Awa").lastName("Traoré").establishment(establishment)
                .contacts(new HashSet<>(Set.of(ContactEntity.builder()
                        .type(ContactType.EMAIL).value(email).isPrimary(true).build())))
                .build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, "n/a", List.of()));
    }
}
