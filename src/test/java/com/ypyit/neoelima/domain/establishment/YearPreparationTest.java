package com.ypyit.neoelima.domain.establishment;

import com.ypyit.neoelima.AbstractIntegrationTest;
import com.ypyit.neoelima.common.exception.BadRequestException;
import com.ypyit.neoelima.domain.establishment.dto.StudentDto;
import com.ypyit.neoelima.domain.establishment.dto.YearPreparationDto;
import com.ypyit.neoelima.domain.establishment.entity.AcademicYearEntity;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.FeeEntity;
import com.ypyit.neoelima.domain.establishment.entity.InstallmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.LevelOfStudyEntity;
import com.ypyit.neoelima.domain.establishment.entity.SchoolClassEntity;
import com.ypyit.neoelima.domain.establishment.entity.SchoolClassYearEntity;
import com.ypyit.neoelima.domain.establishment.entity.SchoolYearEntity;
import com.ypyit.neoelima.domain.establishment.entity.StaffEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentEnrollmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentEntity;
import com.ypyit.neoelima.domain.establishment.enums.SchoolYearStatus;
import com.ypyit.neoelima.domain.establishment.enums.StaffRole;
import com.ypyit.neoelima.domain.establishment.form.AcademicYearForm;
import com.ypyit.neoelima.domain.establishment.form.ReenrollmentForm;
import com.ypyit.neoelima.domain.establishment.form.StudentSearchForm;
import com.ypyit.neoelima.domain.establishment.repository.AcademicYearRepository;
import com.ypyit.neoelima.domain.establishment.repository.EstablishmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.FeeRepository;
import com.ypyit.neoelima.domain.establishment.repository.InstallmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.LevelOfStudyRepository;
import com.ypyit.neoelima.domain.establishment.repository.SchoolClassRepository;
import com.ypyit.neoelima.domain.establishment.repository.SchoolClassYearRepository;
import com.ypyit.neoelima.domain.establishment.repository.SchoolYearRepository;
import com.ypyit.neoelima.domain.establishment.repository.StaffRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentEnrollmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentRepository;
import com.ypyit.neoelima.domain.establishment.service.AcademicYearService;
import com.ypyit.neoelima.domain.establishment.service.SchoolYearClosureService;
import com.ypyit.neoelima.domain.establishment.service.StudentService;
import com.ypyit.neoelima.domain.establishment.service.YearPreparationService;
import com.ypyit.neoelima.domain.user.entity.AdminUserEntity;
import com.ypyit.neoelima.domain.user.entity.ContactEntity;
import com.ypyit.neoelima.domain.user.entity.EstablishmentUserEntity;
import com.ypyit.neoelima.domain.user.entity.TranslateEntity;
import com.ypyit.neoelima.domain.user.enums.ContactType;
import com.ypyit.neoelima.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La rentrée : ouvrir les classes de l'année suivante, réinscrire les élèves, puis basculer.
 *
 * <p>Une école, une classe de CP1 (titulaire Koffi) et une de CP2 ouvertes cette année, un élève en
 * CP1. On prépare l'année suivante sans rien recréer, et sans que l'année en cours ne bouge.
 */
@Transactional
class YearPreparationTest extends AbstractIntegrationTest {

    @Autowired
    private YearPreparationService preparationService;
    @Autowired
    private AcademicYearService academicYearService;
    @Autowired
    private SchoolYearClosureService closureService;
    @Autowired
    private StudentService studentService;
    @Autowired
    private AcademicYearRepository academicYearRepository;
    @Autowired
    private SchoolYearRepository schoolYearRepository;
    @Autowired
    private EstablishmentRepository establishmentRepository;
    @Autowired
    private LevelOfStudyRepository levelOfStudyRepository;
    @Autowired
    private SchoolClassRepository schoolClassRepository;
    @Autowired
    private SchoolClassYearRepository schoolClassYearRepository;
    @Autowired
    private StaffRepository staffRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private StudentEnrollmentRepository studentEnrollmentRepository;
    @Autowired
    private FeeRepository feeRepository;
    @Autowired
    private InstallmentRepository installmentRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EntityManager entityManager;

    private EstablishmentEntity school;
    private AcademicYearEntity current;
    private AcademicYearEntity next;
    private SchoolClassEntity cp1;
    private SchoolClassEntity cp2;
    private StudentEntity pupil;
    private String director;
    private String admin;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        LevelOfStudyEntity levelCp2 = this.level("CP2-" + suffix, "CP2", null);
        LevelOfStudyEntity levelCp1 = this.level("CP1-" + suffix, "CP1", levelCp2.getCode());
        this.current = this.year("2026-2027", 2026, true);
        this.next = this.year("2027-2028", 2027, false);
        this.school = this.establishmentRepository.saveAndFlush(EstablishmentEntity.builder()
                .name("École " + UUID.randomUUID()).active(true).isPrimary(true).build());
        this.school.getLevelOfStudies().addAll(List.of(levelCp1, levelCp2));
        this.establishmentRepository.saveAndFlush(this.school);

        StaffEntity koffi = this.staffRepository.saveAndFlush(StaffEntity.builder()
                .firstName("Serge").lastName("KOFFI").role(StaffRole.TEACHER).active(true)
                .establishment(this.school).build());
        this.cp1 = this.schoolClass("CP1 A", levelCp1);
        this.cp2 = this.schoolClass("CP2 A", levelCp2);
        this.schoolClassYearRepository.saveAndFlush(SchoolClassYearEntity.builder()
                .schoolClass(this.cp1).academicYear(this.current).mainTeacher(koffi).build());
        this.schoolClassYearRepository.saveAndFlush(SchoolClassYearEntity.builder()
                .schoolClass(this.cp2).academicYear(this.current).build());

        this.pupil = this.studentRepository.saveAndFlush(StudentEntity.builder()
                .firstName("Awa").lastName("KONE").registrationNumber("2026001")
                .levelOfStudy(levelCp1).schoolClass(this.cp1).establishment(this.school).build());
        this.studentEnrollmentRepository.saveAndFlush(StudentEnrollmentEntity.builder()
                .student(this.pupil).academicYear(this.current).schoolClass(this.cp1).levelOfStudy(levelCp1).build());

        // La scolarité de CP2 pour l'année suivante.
        FeeEntity fee = this.feeRepository.saveAndFlush(FeeEntity.builder()
                .name("Scolarité CP2").price(new BigDecimal("300000")).academical(true)
                .establishment(this.school).academicYear(this.next).build());
        fee.getLevelOfStudies().add(levelCp2);
        this.feeRepository.saveAndFlush(fee);

        this.director = this.user(EstablishmentUserEntity.builder().establishment(this.school));
        this.admin = this.user(AdminUserEntity.builder());
        this.login(this.director);
    }

    @Test
    @DisplayName("on rouvre les classes de l'an passé d'un geste, titulaire repris")
    void opensLastYearClasses() {
        this.academicYearService.consult(this.next.getId());

        this.preparationService.openClasses(this.next.getId(), List.of(this.cp1.getId(), this.cp2.getId()));

        YearPreparationDto preparation = this.preparationService.preparation(this.next.getId());
        YearPreparationDto.ClassLine line = preparation.getClasses().stream()
                .filter(c -> c.getClassId().equals(this.cp1.getId().toString())).findFirst().orElseThrow();
        assertThat(line.isOpenedInPrevious()).isTrue();
        assertThat(line.isOpenedInTarget()).isTrue();
        assertThat(line.getTargetTeacherName()).isEqualTo("KOFFI Serge");
        // Et l'élève de CP1 est proposé pour la classe du niveau suivant.
        assertThat(preparation.getGroups()).singleElement().satisfies(group -> {
            assertThat(group.getSourceClassName()).isEqualTo("CP1 A");
            assertThat(group.getSuggestedTargetClassId()).isEqualTo(this.cp2.getId().toString());
        });
    }

    @Test
    @DisplayName("réinscrit, l'élève doit les frais de son nouveau niveau ; l'année en cours ne bouge pas")
    void reenrollsWithoutTouchingTheCurrentYear() {
        this.academicYearService.consult(this.next.getId());
        this.preparationService.openClasses(this.next.getId(), List.of(this.cp1.getId(), this.cp2.getId()));

        int done = this.preparationService.reenroll(this.next.getId(), this.form(this.cp2.getId()));
        // Réinscrire deux fois ne le facture pas deux fois.
        int again = this.preparationService.reenroll(this.next.getId(), this.form(this.cp2.getId()));

        assertThat(done).isEqualTo(1);
        assertThat(again).isZero();
        List<InstallmentEntity> owed = this.installmentRepository.findAll().stream()
                .filter(i -> i.getStudentFee().getStudent().getId().equals(this.pupil.getId())).toList();
        assertThat(owed).hasSize(1);
        assertThat(owed.getFirst().getAmount()).isEqualByComparingTo("300000");

        // L'année suivante le voit en CP2…
        assertThat(this.onlyStudent().getSchoolClass().getName()).isEqualTo("CP2 A");
        // … l'année en cours toujours en CP1.
        this.academicYearService.consult(this.current.getId());
        assertThat(this.onlyStudent().getSchoolClass().getName()).isEqualTo("CP1 A");
    }

    @Test
    @DisplayName("une classe qui a des inscrits pour l'année ne se ferme pas")
    void cannotCloseAClassWithEnrollments() {
        this.academicYearService.consult(this.next.getId());
        this.preparationService.openClasses(this.next.getId(), List.of(this.cp2.getId()));
        this.preparationService.reenroll(this.next.getId(), this.form(this.cp2.getId()));

        assertThatThrownBy(() -> this.preparationService.closeClass(this.next.getId(), this.cp2.getId()))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("quand l'année suivante devient l'année en cours, la classe actuelle des réinscrits suit")
    void switchingTheCurrentYearAlignsStudents() {
        this.academicYearService.consult(this.next.getId());
        this.preparationService.openClasses(this.next.getId(), List.of(this.cp2.getId()));
        this.preparationService.reenroll(this.next.getId(), this.form(this.cp2.getId()));

        this.login(this.admin);
        AcademicYearForm form = new AcademicYearForm();
        form.setLabel("2027-2028");
        form.setStartDate(this.next.getStartDate());
        form.setEndDate(this.next.getEndDate());
        form.setActive(true);
        this.academicYearService.update(this.next.getId(), form);
        this.entityManager.flush();
        this.entityManager.clear();

        assertThat(this.studentRepository.findById(this.pupil.getId()).orElseThrow().getSchoolClass().getId())
                .isEqualTo(this.cp2.getId());
    }

    @Test
    @DisplayName("seule la plateforme rouvre une année clôturée, et c'est tracé")
    void onlyThePlatformReopens() {
        SchoolYearEntity closed = this.schoolYearRepository.saveAndFlush(SchoolYearEntity.builder()
                .establishment(this.school).academicYear(this.next).status(SchoolYearStatus.CLOSED).build());

        assertThatThrownBy(() -> this.closureService.reopen(this.next.getId(), this.school.getId()))
                .isInstanceOf(AccessDeniedException.class);

        this.login(this.admin);
        this.closureService.reopen(this.next.getId(), this.school.getId());

        assertThat(this.schoolYearRepository.findById(closed.getId()).orElseThrow().getStatus())
                .isEqualTo(SchoolYearStatus.OPEN);
        assertThat(this.closureService.schools(this.next.getId())).singleElement()
                .satisfies(state -> assertThat(state.getStatus()).isEqualTo(SchoolYearStatus.OPEN));
    }

    private StudentDto onlyStudent() {
        return this.studentService.search(new StudentSearchForm(), PageRequest.of(0, 10)).getContent().getFirst();
    }

    private ReenrollmentForm form(UUID classId) {
        ReenrollmentForm.Item item = new ReenrollmentForm.Item();
        item.setStudentId(this.pupil.getId());
        item.setSchoolClassId(classId);
        ReenrollmentForm form = new ReenrollmentForm();
        form.setItems(List.of(item));
        return form;
    }

    private SchoolClassEntity schoolClass(String name, LevelOfStudyEntity level) {
        return this.schoolClassRepository.saveAndFlush(SchoolClassEntity.builder()
                .name(name).capacity(30).levelOfStudy(level).establishment(this.school).build());
    }

    private LevelOfStudyEntity level(String code, String label, String next) {
        return this.levelOfStudyRepository.saveAndFlush(LevelOfStudyEntity.builder()
                .code(code).position(1).next(next)
                .name(TranslateEntity.builder().fr(label).en(label).build()).build());
    }

    private AcademicYearEntity year(String label, int startYear, boolean active) {
        return this.academicYearRepository.saveAndFlush(AcademicYearEntity.builder()
                .label(label).startDate(LocalDate.of(startYear, 9, 1))
                .endDate(LocalDate.of(startYear + 1, 7, 31)).active(active).build());
    }

    private String user(com.ypyit.neoelima.domain.user.entity.UserEntity.UserEntityBuilder<?, ?> builder) {
        String email = "u-" + UUID.randomUUID() + "@ecole.ci";
        this.userRepository.saveAndFlush(builder.firstName("Awa").lastName("Traoré")
                .contacts(new HashSet<>(Set.of(ContactEntity.builder()
                        .type(ContactType.EMAIL).value(email).isPrimary(true).build())))
                .build());
        return email;
    }

    private void login(String email) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, "n/a", List.of()));
    }
}
