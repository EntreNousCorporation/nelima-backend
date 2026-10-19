package com.ypyit.neoelima.domain.canteen;

import com.ypyit.neoelima.AbstractIntegrationTest;
import com.ypyit.neoelima.common.exception.BadRequestException;
import com.ypyit.neoelima.domain.canteen.dto.CanteenDtos;
import com.ypyit.neoelima.domain.canteen.enums.CanteenAttendanceStatus;
import com.ypyit.neoelima.domain.canteen.enums.DietKind;
import com.ypyit.neoelima.domain.canteen.form.CanteenForms;
import com.ypyit.neoelima.domain.canteen.service.CanteenMenuService;
import com.ypyit.neoelima.domain.canteen.service.CanteenService;
import com.ypyit.neoelima.domain.canteen.service.StudentDietService;
import com.ypyit.neoelima.domain.establishment.entity.AcademicYearEntity;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.InstallmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentEnrollmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentEntity;
import com.ypyit.neoelima.domain.establishment.enums.InstallmentStatus;
import com.ypyit.neoelima.domain.establishment.repository.AcademicYearRepository;
import com.ypyit.neoelima.domain.establishment.repository.EstablishmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.InstallmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentEnrollmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentRepository;
import com.ypyit.neoelima.domain.user.entity.ContactEntity;
import com.ypyit.neoelima.domain.user.entity.EstablishmentUserEntity;
import com.ypyit.neoelima.domain.user.entity.StudentParentUserEntity;
import com.ypyit.neoelima.domain.user.enums.ContactType;
import com.ypyit.neoelima.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cantine : facturation mensuelle à l'abonnement, pointage par exception, régimes jusqu'à la
 * cuisine, menu publié aux familles.
 */
@Transactional
class CanteenServiceTest extends AbstractIntegrationTest {

    /** Un mercredi : la formule 4 jours ne le sert pas. */
    private static final LocalDate WEDNESDAY = LocalDate.of(2026, 10, 14);
    private static final LocalDate THURSDAY = LocalDate.of(2026, 10, 15);

    @Autowired
    private CanteenService canteenService;
    @Autowired
    private CanteenMenuService menuService;
    @Autowired
    private StudentDietService dietService;
    @Autowired
    private AcademicYearRepository academicYearRepository;
    @Autowired
    private EstablishmentRepository establishmentRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private StudentEnrollmentRepository enrollmentRepository;
    @Autowired
    private InstallmentRepository installmentRepository;
    @Autowired
    private UserRepository userRepository;

    private EstablishmentEntity school;
    private AcademicYearEntity year;
    private StudentEntity awa;
    private StudentEntity yao;
    private CanteenDtos.Formula fourDays;
    private CanteenDtos.Formula occasional;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        this.year = this.academicYearRepository.saveAndFlush(AcademicYearEntity.builder()
                .label("2026-2027").startDate(LocalDate.of(2026, 9, 1)).endDate(LocalDate.of(2027, 6, 30))
                .active(true).build());
        this.school = this.establishmentRepository.saveAndFlush(EstablishmentEntity.builder()
                .name("École " + UUID.randomUUID()).active(true).isPrimary(true).build());
        this.awa = this.student("KONE", "Awa");
        this.yao = this.student("KOUAME", "Yao");
        this.login(this.user(EstablishmentUserEntity.builder().establishment(this.school)));

        this.fourDays = this.canteenService.createFormula(this.formula("4 jours", "15000",
                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY));
        this.occasional = this.canteenService.createFormula(this.formula("Ponctuel", "8000"));
    }

    @Test
    @DisplayName("s'abonner crée une tranche par mois jusqu'à la fin de l'année ; arrêter annule les suivantes")
    void billsMonthlyAndStopsCleanly() {
        CanteenDtos.Subscriber row = this.subscribe(this.awa, this.fourDays, LocalDate.of(2026, 10, 12));

        List<InstallmentEntity> months = this.installmentsOf(this.awa);
        // Octobre à juin : neuf mois.
        assertThat(months).hasSize(9);
        assertThat(months).allSatisfy(m -> assertThat(m.getAmount()).isEqualByComparingTo("15000"));
        // Arrivée le 12 : la première échéance est le jour d'arrivée, pas le 5.
        assertThat(months.stream().map(InstallmentEntity::getDueDate).min(LocalDate::compareTo).orElseThrow())
                .isEqualTo(LocalDate.of(2026, 10, 12));

        assertThatThrownBy(() -> this.subscribe(this.awa, this.occasional, LocalDate.of(2026, 11, 2)))
                .isInstanceOf(BadRequestException.class);

        CanteenForms.End end = new CanteenForms.End();
        end.setEndDate(LocalDate.of(2026, 12, 18));
        this.canteenService.end(UUID.fromString(row.getSubscriptionId()), end);

        assertThat(this.installmentsOf(this.awa).stream().filter(m -> m.getStatus() == InstallmentStatus.PENDING))
                .hasSize(3);
    }

    @Test
    @DisplayName("un abonné est servi les jours de sa formule, sauf absence ; un ponctuel quand on le pointe")
    void attendanceByException() {
        this.subscribe(this.awa, this.fourDays, LocalDate.of(2026, 10, 1));
        this.subscribe(this.yao, this.occasional, LocalDate.of(2026, 10, 1));

        CanteenDtos.Overview thursday = this.canteenService.overview(THURSDAY);
        assertThat(thursday.getExpectedToday()).isEqualTo(1);
        assertThat(thursday.getServedToday()).isEqualTo(1);
        // Le mercredi n'est pas un jour de la formule 4 jours.
        assertThat(this.canteenService.overview(WEDNESDAY).getServedToday()).isZero();

        this.mark(this.awa, THURSDAY, CanteenAttendanceStatus.ABSENT);
        this.mark(this.yao, THURSDAY, CanteenAttendanceStatus.SERVED);

        CanteenDtos.Overview marked = this.canteenService.overview(THURSDAY);
        assertThat(marked.getServedToday()).isEqualTo(1);
        assertThat(marked.getAbsentToday()).isEqualTo(1);
    }

    @Test
    @DisplayName("les régimes du dossier arrivent à la fiche cuisine, pour les seuls élèves servis")
    void dietsReachTheKitchen() {
        this.subscribe(this.awa, this.fourDays, LocalDate.of(2026, 10, 1));
        CanteenForms.Diet pork = new CanteenForms.Diet();
        pork.setKind(DietKind.NO_PORK);
        CanteenForms.Diet peanut = new CanteenForms.Diet();
        peanut.setKind(DietKind.ALLERGY_PEANUT);
        this.dietService.replace(this.awa.getId(), List.of(pork, peanut));

        assertThat(this.canteenService.overview(THURSDAY).getAllergies()).isEqualTo(1);
        assertThat(this.canteenService.kitchenSheet(THURSDAY)).extracting(CanteenDtos.KitchenLine::getKind)
                .containsExactlyInAnyOrder(DietKind.NO_PORK, DietKind.ALLERGY_PEANUT);

        this.mark(this.awa, THURSDAY, CanteenAttendanceStatus.ABSENT);
        assertThat(this.canteenService.kitchenSheet(THURSDAY)).isEmpty();
    }

    @Test
    @DisplayName("les familles ne voient que le menu publié")
    void familiesSeeOnlyThePublishedMenu() {
        CanteenForms.MenuDay monday = new CanteenForms.MenuDay();
        monday.setDay(LocalDate.of(2026, 10, 12));
        monday.setMainCourse("Attiéké, poisson braisé");
        monday.setAllergens(List.of("Poisson"));
        CanteenForms.Menu menu = new CanteenForms.Menu();
        menu.setDays(List.of(monday));
        this.menuService.save(menu);

        String parent = this.user(StudentParentUserEntity.builder());
        this.awa.getParentUsers().add((StudentParentUserEntity) this.userRepository.findAll().stream()
                .filter(u -> u instanceof StudentParentUserEntity && u.getContacts().stream().anyMatch(c -> c.getValue().equals(parent)))
                .findFirst().orElseThrow());
        this.studentRepository.saveAndFlush(this.awa);

        this.login(parent);
        assertThat(this.menuService.mine(LocalDate.of(2026, 10, 14)).values()).allSatisfy(week -> assertThat(week).isEmpty());

        this.login(this.schoolUser);
        this.menuService.publish(LocalDate.of(2026, 10, 14), true);
        this.login(parent);
        assertThat(this.menuService.mine(LocalDate.of(2026, 10, 14)).values()).singleElement()
                .satisfies(week -> assertThat(week).singleElement()
                        .satisfies(day -> assertThat(day.getMainCourse()).isEqualTo("Attiéké, poisson braisé")));
    }

    private String schoolUser;

    private CanteenDtos.Subscriber subscribe(StudentEntity student, CanteenDtos.Formula formula, LocalDate start) {
        CanteenForms.Subscription form = new CanteenForms.Subscription();
        form.setStudentId(student.getId());
        form.setFormulaId(UUID.fromString(formula.getId()));
        form.setStartDate(start);
        return this.canteenService.subscribe(form);
    }

    private void mark(StudentEntity student, LocalDate day, CanteenAttendanceStatus status) {
        CanteenForms.Attendance form = new CanteenForms.Attendance();
        form.setStudentId(student.getId());
        form.setDay(day);
        form.setStatus(status);
        this.canteenService.mark(form);
    }

    private List<InstallmentEntity> installmentsOf(StudentEntity student) {
        return this.installmentRepository.findAll().stream()
                .filter(i -> i.getStudentFee().getStudent().getId().equals(student.getId())).toList();
    }

    private CanteenForms.Formula formula(String name, String price, DayOfWeek... days) {
        CanteenForms.Formula form = new CanteenForms.Formula();
        form.setName(name);
        form.setMonthlyPrice(new BigDecimal(price));
        form.setDays(List.of(days));
        return form;
    }

    private StudentEntity student(String lastName, String firstName) {
        StudentEntity student = this.studentRepository.saveAndFlush(StudentEntity.builder()
                .firstName(firstName).lastName(lastName).registrationNumber(UUID.randomUUID().toString().substring(0, 8))
                .establishment(this.school).build());
        this.enrollmentRepository.saveAndFlush(StudentEnrollmentEntity.builder().student(student).academicYear(this.year).build());
        return student;
    }

    private String user(com.ypyit.neoelima.domain.user.entity.UserEntity.UserEntityBuilder<?, ?> builder) {
        String email = "u-" + UUID.randomUUID() + "@ecole.ci";
        this.userRepository.saveAndFlush(builder.firstName("Awa").lastName("Traoré")
                .contacts(new HashSet<>(Set.of(ContactEntity.builder()
                        .type(ContactType.EMAIL).value(email).isPrimary(true).build())))
                .build());
        if (builder instanceof EstablishmentUserEntity.EstablishmentUserEntityBuilder<?, ?>) {
            this.schoolUser = email;
        }
        return email;
    }

    private void login(String email) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, "n/a", List.of()));
    }
}
