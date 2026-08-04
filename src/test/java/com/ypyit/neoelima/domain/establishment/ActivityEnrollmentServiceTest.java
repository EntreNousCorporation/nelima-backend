package com.ypyit.neoelima.domain.establishment;

import com.ypyit.neoelima.AbstractIntegrationTest;
import com.ypyit.neoelima.common.exception.BadRequestException;
import com.ypyit.neoelima.common.exception.NotFoundException;
import com.ypyit.neoelima.domain.establishment.dto.ActivityDto;
import com.ypyit.neoelima.domain.establishment.dto.ActivityEnrollmentDto;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.InstallmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.LevelOfStudyEntity;
import com.ypyit.neoelima.domain.establishment.entity.SchoolClassEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentEntity;
import com.ypyit.neoelima.domain.establishment.enums.ActivityKind;
import com.ypyit.neoelima.domain.establishment.enums.ActivityStatus;
import com.ypyit.neoelima.domain.establishment.enums.EnrollmentSource;
import com.ypyit.neoelima.domain.establishment.enums.EnrollmentStatus;
import com.ypyit.neoelima.domain.establishment.enums.InstallmentStatus;
import com.ypyit.neoelima.domain.establishment.form.ActivityClassesForm;
import com.ypyit.neoelima.domain.establishment.form.ActivityForm;
import com.ypyit.neoelima.domain.establishment.repository.EstablishmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.InstallmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.LevelOfStudyRepository;
import com.ypyit.neoelima.domain.establishment.repository.SchoolClassRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentFeeRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentRepository;
import com.ypyit.neoelima.domain.establishment.service.ActivityEnrollmentService;
import com.ypyit.neoelima.domain.establishment.service.ActivityService;
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
import org.springframework.beans.factory.annotation.Autowired;
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
 * Inscriptions aux activités extra-scolaires.
 *
 * <p>Quatre erreurs seraient coûteuses et silencieuses : facturer une place qu'on n'a pas donnée,
 * oublier de facturer celle qu'on vient de donner, effacer une dette déjà encaissée, et inscrire
 * l'enfant d'un autre.
 */
@Transactional
class ActivityEnrollmentServiceTest extends AbstractIntegrationTest {

    @Autowired
    private ActivityService activityService;
    @Autowired
    private ActivityEnrollmentService enrollmentService;
    @Autowired
    private EstablishmentRepository establishmentRepository;
    @Autowired
    private SchoolClassRepository schoolClassRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private LevelOfStudyRepository levelOfStudyRepository;
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

    private EstablishmentEntity school;
    private SchoolClassEntity cm2;
    private SchoolClassEntity cp1;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        this.school = this.school();
        this.authenticateOn(this.school);
        this.cm2 = this.schoolClass(this.school, "CM2 A");
        this.cp1 = this.schoolClass(this.school, "CP1 A");
    }

    @Test
    @DisplayName("l'élève d'une classe non conviée ne peut pas être inscrit")
    void refusesAStudentOutsideTheInvitedClasses() {
        ActivityDto judo = this.activity("Judo", 10, new BigDecimal("30000"));
        this.activityService.assignClasses(UUID.fromString(judo.getId()),
                this.classes(false, this.cm2));
        StudentEntity elsewhere = this.student("KOUAMÉ", this.cp1);

        assertThatThrownBy(() -> this.enrollmentService.enroll(UUID.fromString(judo.getId()),
                List.of(elsewhere.getId()), EnrollmentSource.SCHOOL))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("l'élève sans classe n'est convié que par une activité ouverte à tous")
    void aStudentWithoutAClassOnlyJoinsOpenActivities() {
        ActivityDto chorale = this.activity("Chorale", 10, null);
        this.activityService.assignClasses(UUID.fromString(chorale.getId()),
                this.classes(false, this.cm2));
        StudentEntity unassigned = this.student("BROU", null);

        assertThatThrownBy(() -> this.enrollmentService.enroll(UUID.fromString(chorale.getId()),
                List.of(unassigned.getId()), EnrollmentSource.SCHOOL))
                .isInstanceOf(BadRequestException.class);

        this.activityService.assignClasses(UUID.fromString(chorale.getId()), this.classes(true));
        assertThat(this.enrollmentService.enroll(UUID.fromString(chorale.getId()),
                List.of(unassigned.getId()), EnrollmentSource.SCHOOL))
                .singleElement()
                .extracting(ActivityEnrollmentDto::getStatus)
                .isEqualTo(EnrollmentStatus.ENROLLED);
    }

    @Test
    @DisplayName("une inscription sous la capacité crée la dette")
    void billsOnEnrollment() {
        ActivityDto judo = this.activity("Judo", 10, new BigDecimal("30000"));
        this.activityService.assignClasses(UUID.fromString(judo.getId()), this.classes(true));
        StudentEntity student = this.student("ZOKOU", this.cm2);

        ActivityEnrollmentDto enrollment = this.enrollmentService.enroll(
                UUID.fromString(judo.getId()), List.of(student.getId()), EnrollmentSource.SCHOOL)
                .getFirst();

        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
        assertThat(enrollment.getAmountDue()).isEqualByComparingTo(new BigDecimal("30000"));
    }

    @Test
    @DisplayName("au-delà de la capacité, l'inscription attend et ne produit aucune dette")
    void doesNotBillTheWaitingList() {
        ActivityDto judo = this.activity("Judo", 1, new BigDecimal("30000"));
        this.activityService.assignClasses(UUID.fromString(judo.getId()), this.classes(true));
        StudentEntity first = this.student("AHOU", this.cm2);
        StudentEntity second = this.student("YEO", this.cm2);

        this.enrollmentService.enroll(UUID.fromString(judo.getId()), List.of(first.getId()),
                EnrollmentSource.SCHOOL);
        ActivityEnrollmentDto waiting = this.enrollmentService.enroll(UUID.fromString(judo.getId()),
                List.of(second.getId()), EnrollmentSource.SCHOOL).getFirst();

        // On ne facture pas une place qu'on n'a pas.
        assertThat(waiting.getStatus()).isEqualTo(EnrollmentStatus.WAITLISTED);
        assertThat(waiting.getAmountDue()).isNull();
        assertThat(this.studentFeeRepository.findAll().stream()
                .filter(fee -> fee.getStudent().getId().equals(second.getId()))).isEmpty();
    }

    @Test
    @DisplayName("la place libérée revient au premier demandeur, et sa dette naît alors")
    void promotesTheFirstOnTheWaitingList() {
        ActivityDto judo = this.activity("Judo", 1, new BigDecimal("30000"));
        UUID judoId = UUID.fromString(judo.getId());
        this.activityService.assignClasses(judoId, this.classes(true));
        StudentEntity holder = this.student("AHOU", this.cm2);
        StudentEntity waiting = this.student("YEO", this.cm2);
        this.enrollmentService.enroll(judoId, List.of(holder.getId()), EnrollmentSource.SCHOOL);
        this.enrollmentService.enroll(judoId, List.of(waiting.getId()), EnrollmentSource.SCHOOL);

        this.enrollmentService.cancel(judoId, holder.getId());

        ActivityEnrollmentDto promoted = this.enrollmentService.findAll().stream()
                .filter(line -> line.getStudentId().equals(waiting.getId().toString()))
                .findFirst().orElseThrow();
        assertThat(promoted.getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
        assertThat(promoted.getAmountDue()).isEqualByComparingTo(new BigDecimal("30000"));
    }

    @Test
    @DisplayName("une inscription déjà encaissée ne s'annule pas")
    void refusesToCancelWhatWasPaid() {
        ActivityDto judo = this.activity("Judo", 10, new BigDecimal("30000"));
        UUID judoId = UUID.fromString(judo.getId());
        this.activityService.assignClasses(judoId, this.classes(true));
        StudentEntity student = this.student("DIABATÉ", this.cm2);
        this.enrollmentService.enroll(judoId, List.of(student.getId()), EnrollmentSource.SCHOOL);

        InstallmentEntity installment = this.installmentRepository.findAll().stream()
                .filter(line -> line.getStudentFee().getStudent().getId().equals(student.getId()))
                .findFirst().orElseThrow();
        installment.setStatus(InstallmentStatus.PAID);
        this.installmentRepository.saveAndFlush(installment);

        // Les remboursements sont hors V1 : effacer la dette laisserait un encaissement sans
        // contrepartie et personne pour le rendre.
        assertThatThrownBy(() -> this.enrollmentService.cancel(judoId, student.getId()))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("annuler une inscription non réglée efface sa dette")
    void clearsTheDebtOnCancellation() {
        ActivityDto judo = this.activity("Judo", 10, new BigDecimal("30000"));
        UUID judoId = UUID.fromString(judo.getId());
        this.activityService.assignClasses(judoId, this.classes(true));
        StudentEntity student = this.student("SANOGO", this.cm2);
        this.enrollmentService.enroll(judoId, List.of(student.getId()), EnrollmentSource.SCHOOL);

        this.enrollmentService.cancel(judoId, student.getId());

        assertThat(this.installmentRepository.findAll().stream()
                .filter(line -> line.getStudentFee().getStudent().getId().equals(student.getId())))
                .isEmpty();
    }

    @Test
    @DisplayName("une activité en brouillon n'accepte pas d'inscription")
    void refusesEnrollmentOnADraft() {
        ActivityForm form = this.form("Théâtre", 10, null);
        form.setStatus(ActivityStatus.DRAFT);
        ActivityDto draft = this.activityService.create(form);
        this.activityService.assignClasses(UUID.fromString(draft.getId()), this.classes(true));
        StudentEntity student = this.student("EHOUMAN", this.cm2);

        assertThatThrownBy(() -> this.enrollmentService.enroll(UUID.fromString(draft.getId()),
                List.of(student.getId()), EnrollmentSource.SCHOOL))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("la capacité ne descend pas sous le nombre d'inscrits")
    void refusesToShrinkBelowTheEnrolled() {
        ActivityDto judo = this.activity("Judo", 2, null);
        UUID judoId = UUID.fromString(judo.getId());
        this.activityService.assignClasses(judoId, this.classes(true));
        this.enrollmentService.enroll(judoId, List.of(this.student("KIPRÉ", this.cm2).getId()),
                EnrollmentSource.SCHOOL);

        // La réduire ferait sortir quelqu'un sans que personne ne l'ait décidé.
        ActivityForm shrink = this.form("Judo", 0, null);
        shrink.setCapacity(0);
        assertThatThrownBy(() -> this.activityService.update(judoId, shrink))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("les activités d'une autre école sont invisibles")
    void doesNotLeakOtherSchools() {
        this.activity("Judo", 10, null);
        this.authenticateOn(this.school());

        assertThat(this.activityService.findAll()).isEmpty();
    }

    @Test
    @DisplayName("un parent inscrit son enfant, et pas celui d'un autre")
    void parentEnrollsTheirOwnChildOnly() {
        ActivityDto judo = this.activity("Judo", 10, new BigDecimal("30000"));
        UUID judoId = UUID.fromString(judo.getId());
        this.activityService.assignClasses(judoId, this.classes(true));
        StudentEntity mine = this.student("KOUASSI", this.cm2);
        StudentEntity someoneElses = this.student("BINTOU", this.cm2);

        this.authenticateAsParentOf(mine);

        ActivityEnrollmentDto enrolled = this.enrollmentService.enrollMyChild(judoId, mine.getId());
        assertThat(enrolled.getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
        assertThat(enrolled.getSource()).isEqualTo(EnrollmentSource.PARENT);

        assertThatThrownBy(() -> this.enrollmentService.enrollMyChild(judoId, someoneElses.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("le parent ne voit que les activités ouvertes à la classe de son enfant")
    void parentSeesOnlyWhatTheirChildCanJoin() {
        ActivityDto judo = this.activity("Judo", 10, null);
        this.activityService.assignClasses(UUID.fromString(judo.getId()), this.classes(false, this.cm2));
        ActivityDto echecs = this.activity("Échecs", 10, null);
        this.activityService.assignClasses(UUID.fromString(echecs.getId()), this.classes(false, this.cp1));

        StudentEntity child = this.student("TRAORÉ", this.cm2);
        this.authenticateAsParentOf(child);

        assertThat(this.enrollmentService.openTo(child.getId()))
                .singleElement()
                .extracting(ActivityDto::getName)
                .isEqualTo("Judo");
    }

    /* ---------- fabriques ---------- */

    private ActivityDto activity(String name, int capacity, BigDecimal price) {
        return this.activityService.create(this.form(name, capacity, price));
    }

    private ActivityForm form(String name, int capacity, BigDecimal price) {
        ActivityForm form = new ActivityForm();
        form.setName(name);
        form.setKind(ActivityKind.SPORT);
        form.setCapacity(capacity);
        form.setPrice(price);
        form.setStatus(ActivityStatus.ACTIVE);
        return form;
    }

    private ActivityClassesForm classes(boolean openToAll, SchoolClassEntity... classes) {
        ActivityClassesForm form = new ActivityClassesForm();
        form.setOpenToAll(openToAll);
        form.setClassIds(java.util.Arrays.stream(classes).map(SchoolClassEntity::getId).toList());
        return form;
    }

    private EstablishmentEntity school() {
        return this.establishmentRepository.saveAndFlush(EstablishmentEntity.builder()
                .name("École " + UUID.randomUUID()).active(true).isPrimary(true).build());
    }

    private SchoolClassEntity schoolClass(EstablishmentEntity establishment, String name) {
        LevelOfStudyEntity level = this.levelOfStudyRepository.saveAndFlush(LevelOfStudyEntity.builder()
                .code("LVL-" + UUID.randomUUID().toString().substring(0, 8)).position(1)
                .name(TranslateEntity.builder().fr(name).en(name).build())
                .build());
        return this.schoolClassRepository.saveAndFlush(SchoolClassEntity.builder()
                .name(name + " " + UUID.randomUUID().toString().substring(0, 4))
                .capacity(35).levelOfStudy(level).establishment(establishment).build());
    }

    private StudentEntity student(String lastName, SchoolClassEntity schoolClass) {
        return this.studentRepository.saveAndFlush(StudentEntity.builder()
                .firstName("Awa").lastName(lastName)
                .registrationNumber(UUID.randomUUID().toString().substring(0, 8))
                .birthDay(LocalDate.of(2014, 2, 9))
                .schoolClass(schoolClass)
                .establishment(this.school).build());
    }

    private void authenticateAsParentOf(StudentEntity child) {
        String email = "parent-" + UUID.randomUUID() + "@gmail.com";
        StudentParentUserEntity parent = this.userRepository.saveAndFlush(
                StudentParentUserEntity.builder()
                        .firstName("Jean").lastName("Kouassi")
                        .contacts(new HashSet<>(Set.of(ContactEntity.builder()
                                .type(ContactType.EMAIL).value(email).isPrimary(true).build())))
                        .build());
        child.getParentUsers().add(parent);
        this.studentRepository.saveAndFlush(child);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, "n/a", List.of()));
    }

    private void authenticateOn(EstablishmentEntity establishment) {
        Set<PermissionEntity> granted = new HashSet<>();
        for (String code : List.of("activity:read", "activity:write", "class:write")) {
            granted.add(this.permissionRepository.findByCode(code)
                    .orElseGet(() -> this.permissionRepository.saveAndFlush(PermissionEntity.builder()
                            .code(code)
                            .name(TranslateEntity.builder().fr(code).en(code).build())
                            .build())));
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
