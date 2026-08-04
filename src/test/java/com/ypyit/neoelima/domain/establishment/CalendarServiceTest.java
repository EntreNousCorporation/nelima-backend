package com.ypyit.neoelima.domain.establishment;

import com.ypyit.neoelima.AbstractIntegrationTest;
import com.ypyit.neoelima.common.exception.BadRequestException;
import com.ypyit.neoelima.common.service.push.PushNotificationService;
import com.ypyit.neoelima.domain.establishment.dto.CalendarEntryDto;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.FeeEntity;
import com.ypyit.neoelima.domain.establishment.entity.InstallmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.LevelOfStudyEntity;
import com.ypyit.neoelima.domain.establishment.entity.SchoolClassEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentFeeEntity;
import com.ypyit.neoelima.domain.establishment.enums.CalendarEntryKind;
import com.ypyit.neoelima.domain.establishment.enums.InstallmentStatus;
import com.ypyit.neoelima.domain.establishment.enums.SchoolEventKind;
import com.ypyit.neoelima.domain.establishment.form.SchoolEventForm;
import com.ypyit.neoelima.domain.establishment.repository.EstablishmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.FeeRepository;
import com.ypyit.neoelima.domain.establishment.repository.InstallmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.LevelOfStudyRepository;
import com.ypyit.neoelima.domain.establishment.repository.SchoolClassRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentFeeRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentRepository;
import com.ypyit.neoelima.domain.establishment.service.CalendarService;
import com.ypyit.neoelima.domain.establishment.service.SchoolEventService;
import com.ypyit.neoelima.domain.user.entity.ContactEntity;
import com.ypyit.neoelima.domain.user.entity.EstablishmentUserEntity;
import com.ypyit.neoelima.domain.user.entity.PermissionEntity;
import com.ypyit.neoelima.domain.user.entity.RoleEntity;
import com.ypyit.neoelima.domain.user.entity.StudentParentUserEntity;
import com.ypyit.neoelima.domain.user.entity.TranslateEntity;
import com.ypyit.neoelima.domain.user.enums.ContactType;
import com.ypyit.neoelima.domain.user.repository.PermissionRepository;
import com.ypyit.neoelima.domain.user.repository.RoleRepository;
import com.ypyit.neoelima.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Calendrier scolaire.
 *
 * <p>Trois erreurs seraient coûteuses et silencieuses : afficher une échéance qui ne correspond pas
 * à ce que les familles doivent, prévenir deux mille parents parce qu'on a corrigé un titre, et
 * montrer à un parent la vie scolaire d'une classe qui n'est pas celle de son enfant.
 */
@Transactional
class CalendarServiceTest extends AbstractIntegrationTest {

    private static final LocalDate DUE = LocalDate.now().plusDays(10);
    private static final LocalDate FROM = LocalDate.now().minusDays(30);
    private static final LocalDate TO = LocalDate.now().plusDays(60);

    @Autowired
    private CalendarService calendarService;
    @Autowired
    private SchoolEventService schoolEventService;
    @Autowired
    private EstablishmentRepository establishmentRepository;
    @Autowired
    private SchoolClassRepository schoolClassRepository;
    @Autowired
    private LevelOfStudyRepository levelOfStudyRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private FeeRepository feeRepository;
    @Autowired
    private StudentFeeRepository studentFeeRepository;
    @Autowired
    private InstallmentRepository installmentRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PermissionRepository permissionRepository;

    @MockitoBean
    private PushNotificationService pushNotificationService;

    private EstablishmentEntity school;
    private SchoolClassEntity cm2;
    private SchoolClassEntity cp1;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        this.school = this.school();
        this.authenticateOn(this.school, "calendar:read", "calendar:write", "fee:read");
        this.cm2 = this.schoolClass("CM2 A");
        this.cp1 = this.schoolClass("CP1 A");
    }

    @Test
    @DisplayName("une échéance agrège ce que les élèves doivent, et ce qui a été réglé")
    void aggregatesWhatIsDueAndCollected() {
        FeeEntity fee = this.fee("Scolarité T2", "60000");
        this.debt(this.student("KOUAMÉ", this.cm2), fee, "30000", InstallmentStatus.PENDING);
        this.debt(this.student("ZOKOU", this.cm2), fee, "30000", InstallmentStatus.PAID);

        CalendarEntryDto entry = this.entries().stream()
                .filter(line -> CalendarEntryKind.FEE_DUE.equals(line.getKind()))
                .findFirst().orElseThrow();

        assertThat(entry.getTitle()).isEqualTo("Scolarité T2");
        assertThat(entry.getAmountExpected()).isEqualByComparingTo(new BigDecimal("60000"));
        assertThat(entry.getAmountCollected()).isEqualByComparingTo(new BigDecimal("30000"));
        assertThat(entry.getStudentsConcerned()).isEqualTo(2);
        assertThat(entry.getStudentsSettled()).isEqualTo(1);
    }

    @Test
    @DisplayName("une tranche annulée ne gonfle pas l'attendu")
    void ignoresCancelledInstallments() {
        FeeEntity fee = this.fee("Cantine T2", "40000");
        this.debt(this.student("BROU", this.cm2), fee, "20000", InstallmentStatus.PENDING);
        this.debt(this.student("YEO", this.cm2), fee, "20000", InstallmentStatus.CANCELLED);

        CalendarEntryDto entry = this.entries().stream()
                .filter(line -> CalendarEntryKind.FEE_DUE.equals(line.getKind()))
                .findFirst().orElseThrow();

        // La compter réclamerait une somme que personne ne doit.
        assertThat(entry.getAmountExpected()).isEqualByComparingTo(new BigDecimal("20000"));
        assertThat(entry.getStudentsConcerned()).isEqualTo(1);
    }

    @Test
    @DisplayName("sans fee:read, les montants sont absents de la réponse")
    void hidesAmountsWithoutPermission() {
        FeeEntity fee = this.fee("Scolarité T3", "50000");
        this.debt(this.student("DIABATÉ", this.cm2), fee, "50000", InstallmentStatus.PENDING);

        this.authenticateOn(this.school, "calendar:read");
        CalendarEntryDto entry = this.entries().stream()
                .filter(line -> CalendarEntryKind.FEE_DUE.equals(line.getKind()))
                .findFirst().orElseThrow();

        // Le calendrier reste lisible : il perd une colonne, pas sa raison d'être.
        assertThat(entry.getAmountExpected()).isNull();
        assertThat(entry.getAmountCollected()).isNull();
        assertThat(entry.getStudentsConcerned()).isEqualTo(1);
    }

    @Test
    @DisplayName("créer un événement visible ne prévient personne")
    void creatingAVisibleEventNotifiesNobody() {
        this.event("Conseil de classe", SchoolEventKind.SCHOOL_LIFE, true, true);

        // Rendre visible n'est pas interrompre. Sans cette distinction, corriger une faute de
        // frappe renotifierait toute l'école.
        verify(this.pushNotificationService, never()).send(any(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("la notification explicite atteint les tuteurs des classes visées, et eux seuls")
    void notifiesOnlyTheTargetedClasses() {
        StudentEntity concerned = this.student("AHOU", this.cm2);
        StudentEntity elsewhere = this.student("SANOGO", this.cp1);
        UUID targeted = this.attachGuardian(concerned);
        UUID untargeted = this.attachGuardian(elsewhere);

        CalendarEntryDto event = this.event("Réunion CM2", SchoolEventKind.SCHOOL_LIFE, false, true);
        int notified = this.schoolEventService.notifyFamilies(UUID.fromString(event.getId()));

        assertThat(notified).isEqualTo(1);
        ArgumentCaptor<Collection<UUID>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(this.pushNotificationService).send(captor.capture(), anyString(), anyString(), any());
        assertThat(captor.getValue()).containsExactly(targeted).doesNotContain(untargeted);
    }

    @Test
    @DisplayName("un événement non visible des familles ne se notifie pas")
    void refusesToNotifyAnInvisibleEvent() {
        CalendarEntryDto event = this.event("Réunion interne", SchoolEventKind.SCHOOL_LIFE, true, false);

        // Prévenir d'un événement que l'application ne montre pas enverrait le parent sur un écran
        // vide.
        assertThatThrownBy(() -> this.schoolEventService.notifyFamilies(UUID.fromString(event.getId())))
                .isInstanceOf(BadRequestException.class);
        verify(this.pushNotificationService, never()).send(any(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("le calendrier d'une autre école est invisible")
    void doesNotLeakOtherSchools() {
        this.event("Journée portes ouvertes", SchoolEventKind.SCHOOL_LIFE, true, true);
        this.authenticateOn(this.school(), "calendar:read", "fee:read");

        assertThat(this.entries()).isEmpty();
    }

    @Test
    @DisplayName("le parent ne voit que ce qui concerne son enfant, et ses propres échéances")
    void parentSeesOnlyTheirOwnChild() {
        StudentEntity mine = this.student("KOUASSI", this.cm2);
        StudentEntity someoneElses = this.student("BINTOU", this.cp1);
        FeeEntity fee = this.fee("Scolarité T1", "40000");
        this.debt(mine, fee, "20000", InstallmentStatus.PENDING);
        this.debt(someoneElses, fee, "20000", InstallmentStatus.PENDING);

        this.event("Réunion CM2", SchoolEventKind.SCHOOL_LIFE, false, true);       // vise CM2
        this.event("Réunion interne", SchoolEventKind.SCHOOL_LIFE, true, false);   // non visible

        UUID parentId = this.attachGuardian(mine);
        this.authenticateAsParent(parentId);

        List<CalendarEntryDto> seen = this.calendarService.findMine(FROM, TO);

        assertThat(seen).extracting(CalendarEntryDto::getTitle)
                .containsExactlyInAnyOrder("Réunion CM2", "Scolarité T1");
        // L'agrégat de l'école ne le regarde pas : il voit ce que son enfant doit.
        assertThat(seen.stream()
                .filter(line -> CalendarEntryKind.FEE_DUE.equals(line.getKind()))
                .findFirst().orElseThrow().getStudentsConcerned()).isEqualTo(1);
    }

    @Test
    @DisplayName("une période de plus d'un an est refusée")
    void refusesAnUnboundedRange() {
        // Sans borne, un appel sur dix ans agrégerait toutes les tranches de l'établissement pour
        // rendre une grille que personne ne regarde.
        assertThatThrownBy(() -> this.calendarService.findAll(
                LocalDate.of(2020, 1, 1), LocalDate.of(2030, 1, 1)))
                .isInstanceOf(BadRequestException.class);
    }

    /* ---------- fabriques ---------- */

    private List<CalendarEntryDto> entries() {
        return this.calendarService.findAll(FROM, TO);
    }

    private CalendarEntryDto event(String title, SchoolEventKind kind, boolean wholeSchool,
                                   boolean visible) {
        SchoolEventForm form = new SchoolEventForm();
        form.setTitle(title);
        form.setKind(kind);
        form.setDate(LocalDate.now().plusDays(5));
        form.setAllDay(true);
        form.setWholeSchool(wholeSchool);
        form.setVisibleToFamilies(visible);
        if (!wholeSchool) {
            form.setClassIds(List.of(this.cm2.getId()));
        }
        return this.schoolEventService.create(form);
    }

    private FeeEntity fee(String name, String price) {
        return this.feeRepository.saveAndFlush(FeeEntity.builder()
                .name(name).price(new BigDecimal(price)).establishment(this.school).build());
    }

    private void debt(StudentEntity student, FeeEntity fee, String amount, InstallmentStatus status) {
        StudentFeeEntity studentFee = this.studentFeeRepository.saveAndFlush(StudentFeeEntity.builder()
                .student(student).fee(fee).name(fee.getName()).build());
        this.installmentRepository.saveAndFlush(InstallmentEntity.builder()
                .studentFee(studentFee).label("1er versement")
                .amount(new BigDecimal(amount)).dueDate(DUE).status(status).build());
    }

    private EstablishmentEntity school() {
        return this.establishmentRepository.saveAndFlush(EstablishmentEntity.builder()
                .name("École " + UUID.randomUUID()).active(true).isPrimary(true).build());
    }

    private SchoolClassEntity schoolClass(String name) {
        LevelOfStudyEntity level = this.levelOfStudyRepository.saveAndFlush(LevelOfStudyEntity.builder()
                .code("LVL-" + UUID.randomUUID().toString().substring(0, 8)).position(1)
                .name(TranslateEntity.builder().fr(name).en(name).build()).build());
        return this.schoolClassRepository.saveAndFlush(SchoolClassEntity.builder()
                .name(name + " " + UUID.randomUUID().toString().substring(0, 4))
                .capacity(35).levelOfStudy(level).establishment(this.school).build());
    }

    private StudentEntity student(String lastName, SchoolClassEntity schoolClass) {
        return this.studentRepository.saveAndFlush(StudentEntity.builder()
                .firstName("Awa").lastName(lastName)
                .registrationNumber(UUID.randomUUID().toString().substring(0, 8))
                .birthDay(LocalDate.of(2014, 2, 9))
                .schoolClass(schoolClass).establishment(this.school).build());
    }

    private UUID attachGuardian(StudentEntity child) {
        String email = "parent-" + UUID.randomUUID() + "@gmail.com";
        StudentParentUserEntity parent = this.userRepository.saveAndFlush(
                StudentParentUserEntity.builder()
                        .firstName("Jean").lastName("Kouassi")
                        .contacts(new HashSet<>(Set.of(ContactEntity.builder()
                                .type(ContactType.EMAIL).value(email).isPrimary(true).build())))
                        .build());
        child.getParentUsers().add(parent);
        this.studentRepository.saveAndFlush(child);
        return parent.getId();
    }

    private void authenticateAsParent(UUID parentId) {
        String username = this.userRepository.findById(parentId).orElseThrow().getUsername();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "n/a", List.of()));
    }

    private void authenticateOn(EstablishmentEntity establishment, String... permissions) {
        Set<PermissionEntity> granted = new HashSet<>();
        for (String code : permissions) {
            granted.add(this.permissionRepository.findByCode(code)
                    .orElseGet(() -> this.permissionRepository.saveAndFlush(PermissionEntity.builder()
                            .code(code)
                            .name(TranslateEntity.builder().fr(code).en(code).build()).build())));
        }
        RoleEntity role = this.roleRepository.saveAndFlush(RoleEntity.builder()
                .code("ROLE-" + UUID.randomUUID())
                .name(TranslateEntity.builder().fr("Rôle de test").en("Test role").build())
                .permissions(granted).build());

        String email = "user-" + UUID.randomUUID() + "@ecole.ci";
        this.userRepository.saveAndFlush(EstablishmentUserEntity.builder()
                .firstName("Awa").lastName("Traoré").establishment(establishment).role(role)
                .contacts(new HashSet<>(Set.of(ContactEntity.builder()
                        .type(ContactType.EMAIL).value(email).isPrimary(true).build())))
                .build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, "n/a", List.of()));
    }
}
